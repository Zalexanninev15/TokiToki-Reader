package io.github.zalexanninev15.tokitoki.domain.repository

import io.github.zalexanninev15.tokitoki.domain.model.AccountRef
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncCapability
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncOutcome
import kotlinx.coroutines.flow.Flow

/** Opaque, source-specific pagination token. Never construct one by hand. */
@JvmInline
value class PageCursor(val raw: String)

data class Page(
    val items: List<FeedItem>,
    /** Null when the source has no further pages. */
    val next: PageCursor?,
)

sealed class SourceError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(val accountLocalId: String) : SourceError("token rejected")
    class Forbidden(message: String) : SourceError(message)
    class NotFound(message: String) : SourceError(message)
    class RateLimited(val retryAfterMillis: Long?) : SourceError("rate limited")
    class Server(val code: Int, message: String) : SourceError("server $code: $message")
    class Network(cause: Throwable) : SourceError("network unavailable", cause)
    class InvalidInstance(message: String) : SourceError(message)
    /** The server is reachable but does not implement the endpoint (e.g. no markers). */
    class Unsupported(val endpoint: String) : SourceError("unsupported: $endpoint")
}

/**
 * One backend. Implemented separately per service on purpose: the three have
 * incompatible pagination (Link headers / untilId / TDLib updates) and incompatible text
 * models, and flattening that at the network layer is exactly the mistake that makes
 * these aggregators unmaintainable.
 */
interface TimelineSource {
    val account: AccountRef
    val readSyncCapability: ReadSyncCapability

    suspend fun loadPage(cursor: PageCursor?, limit: Int): Page

    /** Cached items, served immediately and updated as the network catches up. */
    fun observeCached(): Flow<List<FeedItem>>
}

/**
 * Pushes read acknowledgements to the origin server.
 *
 * Implementations receive a batch because all three backends prefer it: TDLib takes a
 * message id array, Mastodon needs only the maximum, and Misskey mostly discards it.
 */
interface ReadSynchronizer {
    val capability: ReadSyncCapability
    suspend fun acknowledge(items: List<FeedItemId>): ReadSyncOutcome
}

interface AccountRepository {
    fun observeAccounts(): Flow<List<AccountRef>>
    suspend fun remove(accountLocalId: String)
    /** Wipes tokens from encrypted storage and any cached content for the account. */
    suspend fun logout(accountLocalId: String)
}

interface FeedRepository {
    fun observeFeed(accountLocalIds: Set<String>): Flow<List<FeedItem>>
    suspend fun refresh(accountLocalIds: Set<String>)
    suspend fun loadMore(accountLocalIds: Set<String>)
    fun observeReadIds(): Flow<Set<String>>
    suspend fun markSeen(ids: List<FeedItemId>)
}
