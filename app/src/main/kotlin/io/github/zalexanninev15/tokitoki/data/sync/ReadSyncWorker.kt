package io.github.zalexanninev15.tokitoki.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.zalexanninev15.tokitoki.TokiTokiApp
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonReadSynchronizer
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyReadSynchronizer
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.readsync.BackoffPolicy
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncOutcome
import io.github.zalexanninev15.tokitoki.domain.repository.ReadSynchronizer
import java.util.concurrent.TimeUnit

/**
 * Drains the read-acknowledgement queue.
 *
 * Runs both on demand (right after the user scrolls past something) and periodically, so
 * an acknowledgement that failed while offline still reaches the server later.
 */
class ReadSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val backoff = BackoffPolicy()

    override suspend fun doWork(): Result {
        val container = (applicationContext as TokiTokiApp).container
        val queueDao = container.database.readQueueDao()
        val accountDao = container.database.accountDao()
        val feedDao = container.database.feedDao()
        val now = System.currentTimeMillis()

        val due = queueDao.due(now)
        if (due.isEmpty()) return Result.success()

        var sawTransientFailure = false

        for ((accountId, entries) in due.groupBy { it.accountLocalId }) {
            // `?: run { ...; continue }` would put `continue` inside an inline lambda,
            // which Kotlin 2.0 still treats as an experimental feature.
            val account = accountDao.byId(accountId)
            if (account == null) {
                queueDao.remove(entries.map { it.itemId })
                continue
            }
            val token = container.secureStore.token(accountId)
            if (token == null) {
                queueDao.remove(entries.map { it.itemId })
                continue
            }

            val synchronizer: ReadSynchronizer = when (SourceKind.valueOf(account.source)) {
                SourceKind.MASTODON -> MastodonReadSynchronizer(
                    api = MastodonClientFactory.create("https://${account.host}"),
                    bearer = "Bearer $token",
                    loadStoredCursor = { container.secureStore.cursor(accountId) },
                    storeCursor = { container.secureStore.putCursor(accountId, it) },
                )

                SourceKind.MISSKEY -> MisskeyReadSynchronizer(
                    api = MisskeyClientFactory.create("https://${account.host}"),
                    token = token,
                )

                SourceKind.TELEGRAM -> continue
            }

            val ids = entries.map {
                FeedItemId(SourceKind.valueOf(account.source), accountId, it.remoteId)
            }

            when (val outcome = synchronizer.acknowledge(ids)) {
                is ReadSyncOutcome.Success -> {
                    feedDao.markRemoteConfirmed(entries.map { it.itemId })
                    queueDao.remove(entries.map { it.itemId })
                }

                // Retrying will not help: drop the queue entries so they do not spin
                // forever. The local read flag stays; only the server side is lost.
                is ReadSyncOutcome.Permanent -> queueDao.remove(entries.map { it.itemId })

                is ReadSyncOutcome.Transient -> {
                    sawTransientFailure = true
                    val attempts = (entries.minOfOrNull { it.attempts } ?: 0) + 1
                    if (backoff.shouldGiveUp(attempts)) {
                        queueDao.remove(entries.map { it.itemId })
                    } else {
                        queueDao.reschedule(
                            accountId = accountId,
                            attempts = attempts,
                            nextAttemptAt = now + (
                                outcome.retryAfterMillis ?: backoff.nextDelayMillis(attempts)
                                ),
                            error = outcome.reason,
                        )
                    }
                }
            }
        }

        return if (sawTransientFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "read-sync-periodic"

        /**
         * Fifteen minutes is WorkManager's own floor for periodic work, and it is also
         * the right order of magnitude here: read state is not urgent, and anything
         * tighter would burn battery against a 300-per-5-minutes rate limit.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReadSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
