package io.github.zalexanninev15.tokitoki.data.mastodon.api

import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncCapability
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncOutcome
import io.github.zalexanninev15.tokitoki.domain.readsync.TimelineCursorPolicy
import io.github.zalexanninev15.tokitoki.domain.repository.ReadSynchronizer
import retrofit2.HttpException
import java.io.IOException

/**
 * Pushes the home-timeline position to `POST /api/v1/markers`.
 *
 * What this can and cannot do, so the UI does not overpromise:
 *  - it moves a single cursor for the whole home timeline;
 *  - it cannot mark item #5 read while leaving #3 unread;
 *  - it is shared with every other client on the account, which is why the cursor is
 *    only ever moved forward (see [TimelineCursorPolicy]).
 */
class MastodonReadSynchronizer(
    private val api: MastodonApi,
    private val bearer: String,
    /** Persisted alongside the account so the cursor survives a restart. */
    private val loadStoredCursor: suspend () -> String?,
    private val storeCursor: suspend (String) -> Unit,
) : ReadSynchronizer {

    @Volatile
    private var capabilityOverride: ReadSyncCapability? = null

    override val capability: ReadSyncCapability
        get() = capabilityOverride ?: ReadSyncCapability.TimelineCursor

    override suspend fun acknowledge(items: List<FeedItemId>): ReadSyncOutcome {
        if (items.isEmpty()) return ReadSyncOutcome.Success

        return try {
            val serverCursor = fetchServerCursor()
            val known = TimelineCursorPolicy.newest(serverCursor, loadStoredCursor())

            val target = TimelineCursorPolicy.advance(known, items.map { it.remoteId })
                ?: return ReadSyncOutcome.Success // already covered; nothing to send

            val response = api.setHomeMarker(bearer, target)
            when {
                response.isSuccessful -> {
                    storeCursor(target)
                    ReadSyncOutcome.Success
                }
                // 409: another client moved the marker between our read and write.
                response.code() == 409 -> ReadSyncOutcome.Transient("marker version conflict")
                else -> mapHttpError(response.code(), response.headers()["Retry-After"])
            }
        } catch (e: IOException) {
            ReadSyncOutcome.Transient("network: ${e.message}")
        } catch (e: HttpException) {
            mapHttpError(e.code(), e.response()?.headers()?.get("Retry-After"))
        }
    }

    private suspend fun fetchServerCursor(): String? {
        val response = api.getMarkers(bearer, listOf("home"))
        if (response.code() == 404) {
            // Pleroma, Akkoma and GoToSocial may not implement markers at all. That is a
            // capability gap, not an error: downgrade and keep the local flag only.
            capabilityOverride = ReadSyncCapability.LocalOnly()
            return null
        }
        return response.body()?.home?.lastReadId
    }

    private fun mapHttpError(code: Int, retryAfter: String?): ReadSyncOutcome = when (code) {
        401 -> ReadSyncOutcome.Permanent("token rejected; re-authentication required")
        403 -> ReadSyncOutcome.Permanent("missing write:statuses scope")
        404 -> ReadSyncOutcome.Permanent("markers not implemented by this server")
        429 -> ReadSyncOutcome.Transient(
            "rate limited",
            retryAfter?.toLongOrNull()?.times(1_000),
        )
        in 500..599 -> ReadSyncOutcome.Transient("server error $code")
        else -> ReadSyncOutcome.Permanent("unexpected status $code")
    }
}
