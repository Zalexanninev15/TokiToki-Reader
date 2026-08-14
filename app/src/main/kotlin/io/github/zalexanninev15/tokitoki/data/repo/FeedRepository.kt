package io.github.zalexanninev15.tokitoki.data.repo

import io.github.zalexanninev15.tokitoki.data.db.AccountDao
import io.github.zalexanninev15.tokitoki.data.db.AccountEntity
import io.github.zalexanninev15.tokitoki.data.db.FeedDao
import io.github.zalexanninev15.tokitoki.data.db.ReadQueueDao
import io.github.zalexanninev15.tokitoki.data.db.ReadQueueEntity
import io.github.zalexanninev15.tokitoki.data.db.toDomain
import io.github.zalexanninev15.tokitoki.data.db.toEntity
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonPostMapper
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonRemoteSource
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyPostMapper
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyRemoteSource
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.repository.Page
import io.github.zalexanninev15.tokitoki.domain.repository.PageCursor
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val CACHE_LIMIT = 2_000

class FeedRepository(
    private val accountDao: AccountDao,
    private val feedDao: FeedDao,
    private val readQueueDao: ReadQueueDao,
    private val secureStore: SecureStore,
) {

    /** Per-account pagination cursors, kept in memory: they are cheap to rebuild. */
    private val cursors = mutableMapOf<String, PageCursor?>()
    /**
     * Accounts with no further pages.
     *
     * Not permanent: a single empty page used to retire an account until the next full
     * refresh, and one hiccup from an instance meant the feed simply stopped growing.
     * [retryExhausted] gives them another chance when the user asks for more and every
     * source claims to be finished.
     */
    private val exhausted = mutableSetOf<String>()
    private var retriedAfterExhaustion = false

    fun observeFeed(accountIds: List<String>): Flow<List<FeedItem>> =
        feedDao.observeFeed(accountIds)
            .distinctUntilChanged()
            // Each row decodes two JSON blobs. Doing that for the whole window on the
            // main thread, on every Room emission, was a large part of why refreshing
            // felt slow.
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeQueueDepth(): Flow<Int> = readQueueDao.observeDepth()

    /**
     * Refreshes every enabled account in parallel.
     *
     * One account failing must not blank the whole feed, so failures are collected and
     * returned instead of thrown: the cached timeline stays on screen and the UI shows a
     * non-blocking error.
     */
    suspend fun refresh(): List<SourceError> = coroutineScope {
        cursors.clear()
        exhausted.clear()
        retriedAfterExhaustion = false
        val accounts = accountDao.enabled()

        val failures = accounts.map { account ->
            async { runCatching { loadInto(account, cursor = null) }.exceptionOrNull() }
        }.awaitAll().filterIsInstance<SourceError>()

        // Once per cycle, not once per page: the trim query sorts the whole table and
        // takes the write lock, so running it per page serialised every parallel fetch.
        feedDao.trimTo(CACHE_LIMIT)
        failures
    }

    /** Ids of accounts the user has switched on, for callers that act on all of them. */
    suspend fun enabledAccountIds(): List<String> = accountDao.enabled().map { it.localId }

    suspend fun loadMore(): List<SourceError> = coroutineScope {
        val enabled = accountDao.enabled()
        var candidates = enabled.filterNot { it.localId in exhausted }

        // Everything claims to be finished: give it one more attempt before the feed
        // stops growing for good. A single empty page from one instance should not be
        // the end of the timeline.
        if (candidates.isEmpty() && !retriedAfterExhaustion) {
            retriedAfterExhaustion = true
            exhausted.clear()
            candidates = enabled
        }

        candidates
            .map { account ->
                async { runCatching { loadInto(account, cursors[account.localId]) }.exceptionOrNull() }
            }
            .awaitAll()
            .filterIsInstance<SourceError>()
            .also { feedDao.trimTo(CACHE_LIMIT) }
    }

    private suspend fun loadInto(account: AccountEntity, cursor: PageCursor?) {
        val token = secureStore.token(account.localId)
            ?: throw SourceError.Unauthorized(account.localId)

        val page: Page = when (SourceKind.valueOf(account.source)) {
            SourceKind.MASTODON -> MastodonRemoteSource(
                api = MastodonClientFactory.create("https://${account.host}"),
                bearer = "Bearer $token",
                mapper = MastodonPostMapper(account.localId, account.host),
            ).loadPage(cursor)

            SourceKind.MISSKEY -> MisskeyRemoteSource(
                api = MisskeyClientFactory.create("https://${account.host}"),
                token = token,
                mapper = MisskeyPostMapper(account.localId, account.host),
            ).loadPage(cursor)

            SourceKind.TELEGRAM -> return
        }

        feedDao.insertAll(page.items.map { it.toEntity() })

        cursors[account.localId] = page.next
        if (page.next == null) exhausted += account.localId
    }

    /**
     * Records that items were actually seen.
     *
     * The local flag is written immediately so the UI never lags behind the user, and the
     * remote acknowledgement is queued separately because it may fail, retry, or — on
     * Misskey — never be possible at all.
     */
    suspend fun markSeen(ids: List<FeedItemId>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()

        feedDao.markLocallyRead(ids.map { it.value })
        readQueueDao.enqueue(
            ids.map {
                ReadQueueEntity(
                    itemId = it.value,
                    accountLocalId = it.accountLocalId,
                    remoteId = it.remoteId,
                    enqueuedAt = now,
                    nextAttemptAt = now,
                )
            },
        )
    }

    suspend fun setAccountEnabled(localId: String, enabled: Boolean) =
        accountDao.setEnabled(localId, enabled)

    suspend fun logout(localId: String) {
        accountDao.delete(localId)
        feedDao.deleteForAccount(localId)
        readQueueDao.clearForAccount(localId)
        secureStore.clearAccount(localId)
    }
}
