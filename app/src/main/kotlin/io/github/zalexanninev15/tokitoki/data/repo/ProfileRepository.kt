package io.github.zalexanninev15.tokitoki.data.repo

import io.github.zalexanninev15.tokitoki.data.db.AccountDao
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonPostMapper
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyPostMapper
import io.github.zalexanninev15.tokitoki.data.misskey.dto.UserNotesRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.UserShowRequest
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError

data class Profile(
    val author: Author,
    val posts: List<FeedItem>,
    val profileUrl: String?,
)

/**
 * Loads one account's profile and recent posts for the in-app profile view.
 *
 * Not cached: a profile is looked at once and then closed, and caching it would mean
 * inventing an eviction policy for data nobody comes back to.
 */
class ProfileRepository(
    private val accountDao: AccountDao,
    private val secureStore: SecureStore,
) {

    suspend fun load(viewerAccountLocalId: String, remoteUserId: String): Profile {
        val account = accountDao.byId(viewerAccountLocalId)
            ?: throw SourceError.NotFound("account $viewerAccountLocalId")
        val token = secureStore.token(viewerAccountLocalId)
            ?: throw SourceError.Unauthorized(viewerAccountLocalId)

        return when (SourceKind.valueOf(account.source)) {
            SourceKind.MASTODON -> loadMastodon(account.host, token, viewerAccountLocalId, remoteUserId)
            SourceKind.MISSKEY -> loadMisskey(account.host, token, viewerAccountLocalId, remoteUserId)
            SourceKind.TELEGRAM -> throw SourceError.Unsupported("telegram profiles")
        }
    }

    private suspend fun loadMastodon(
        host: String,
        token: String,
        viewerAccountLocalId: String,
        remoteUserId: String,
    ): Profile {
        val api = MastodonClientFactory.create("https://$host")
        val bearer = "Bearer $token"
        val dto = api.account(bearer, remoteUserId)
        val mapper = MastodonPostMapper(viewerAccountLocalId, host)
        val statuses = api.accountStatuses(bearer, remoteUserId).body().orEmpty().map(mapper::map)

        return Profile(
            author = Author(
                remoteId = dto.id,
                displayName = dto.displayName.ifBlank { dto.username },
                handle = if ('@' in dto.acct) "@${dto.acct}" else "@${dto.acct}@$host",
                avatarUrl = dto.avatar,
                isBot = dto.bot,
            ),
            posts = statuses,
            profileUrl = dto.url,
        )
    }

    private suspend fun loadMisskey(
        host: String,
        token: String,
        viewerAccountLocalId: String,
        remoteUserId: String,
    ): Profile {
        val api = MisskeyClientFactory.create("https://$host")
        val user = api.userShow(UserShowRequest(i = token, userId = remoteUserId))
        val mapper = MisskeyPostMapper(viewerAccountLocalId, host)
        val notes = api.userNotes(UserNotesRequest(i = token, userId = remoteUserId)).map(mapper::map)

        return Profile(
            author = Author(
                remoteId = user.id,
                displayName = user.name?.takeIf { it.isNotBlank() } ?: user.username,
                handle = "@${user.username}@${user.host ?: host}",
                avatarUrl = user.avatarUrl,
                isBot = user.isBot,
            ),
            posts = notes,
            profileUrl = "https://$host/@${user.username}",
        )
    }
}
