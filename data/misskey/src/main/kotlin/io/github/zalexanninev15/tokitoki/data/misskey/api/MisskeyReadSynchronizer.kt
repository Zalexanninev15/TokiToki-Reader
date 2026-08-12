package io.github.zalexanninev15.tokitoki.data.misskey.api

import io.github.zalexanninev15.tokitoki.data.misskey.dto.NotificationsReadRequest
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncCapability
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncOutcome
import io.github.zalexanninev15.tokitoki.domain.repository.ReadSynchronizer
import java.io.IOException

/**
 * Misskey read synchronisation — deliberately the smallest class of the three.
 *
 * Misskey exposes no timeline read-state: there is no per-note read flag and no cursor
 * equivalent to Mastodon's markers. Inventing an endpoint here would be a lie, so this
 * implementation reports [ReadSyncCapability.LocalOnly] and performs only the two
 * acknowledgements that genuinely exist:
 *
 *  - `notifications/mark-all-as-read` for the notification badge;
 *  - `i/read-all-unread-notes` for notes that mention or are addressed to the user.
 *
 * Ordinary posts from followed accounts stay unread server-side no matter what the user
 * does in this app. The Feeds screen states this in words.
 */
class MisskeyReadSynchronizer(
    private val api: MisskeyApi,
    private val token: String,
    /** Enabled only if the user opted in; it clears the badge across all their clients. */
    private val acknowledgeMentions: Boolean = false,
) : ReadSynchronizer {

    override val capability: ReadSyncCapability = ReadSyncCapability.LocalOnly(
        setOf(
            ReadSyncCapability.PartialScope.NOTIFICATIONS,
            ReadSyncCapability.PartialScope.MENTIONS,
        ),
    )

    override suspend fun acknowledge(items: List<FeedItemId>): ReadSyncOutcome {
        if (items.isEmpty() || !acknowledgeMentions) return ReadSyncOutcome.Success

        return try {
            val response = api.readAllUnreadNotes(NotificationsReadRequest(token))
            when {
                response.isSuccessful -> ReadSyncOutcome.Success
                response.code() == 401 -> ReadSyncOutcome.Permanent("token rejected")
                response.code() == 429 -> ReadSyncOutcome.Transient("rate limited")
                response.code() >= 500 -> ReadSyncOutcome.Transient("server ${response.code()}")
                else -> ReadSyncOutcome.Permanent("unexpected status ${response.code()}")
            }
        } catch (e: IOException) {
            ReadSyncOutcome.Transient("network: ${e.message}")
        }
    }
}
