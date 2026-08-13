package io.github.zalexanninev15.tokitoki.data.repo

import io.github.zalexanninev15.tokitoki.data.db.AccountDao
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.mastodon.internal.LinkHeaderParser
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.dto.FollowingRequest
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.domain.model.FollowedAccount
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError

/**
 * The subscriptions directory.
 *
 * Not cached in Room on purpose: the list is read rarely, changes slowly, and keeping a
 * second copy of every followed account would double the cache for no benefit on a screen
 * the user opens occasionally.
 */
class FollowsRepository(
    private val accountDao: AccountDao,
    private val secureStore: SecureStore,
) {

    /** Walks every page so the screen shows the complete list, not just the first 80. */
    suspend fun load(accountLocalId: String): List<FollowedAccount> {
        val account = accountDao.byId(accountLocalId)
            ?: throw SourceError.NotFound("account $accountLocalId")
        val token = secureStore.token(accountLocalId)
            ?: throw SourceError.Unauthorized(accountLocalId)

        return when (SourceKind.valueOf(account.source)) {
            SourceKind.MASTODON -> loadMastodon(account.host, account.remoteUserId, token, accountLocalId)
            SourceKind.MISSKEY -> loadMisskey(account.host, account.remoteUserId, token, accountLocalId)
            SourceKind.TELEGRAM -> emptyList()
        }
    }

    private suspend fun loadMastodon(
        host: String,
        remoteUserId: String,
        token: String,
        accountLocalId: String,
    ): List<FollowedAccount> {
        val api = MastodonClientFactory.create("https://$host")
        val bearer = "Bearer $token"
        val result = mutableListOf<FollowedAccount>()

        var response = api.following(bearer, remoteUserId)
        var guard = 0
        while (guard++ < MAX_PAGES) {
            if (!response.isSuccessful) break
            val body = response.body().orEmpty()
            body.forEach { dto ->
                result += FollowedAccount(
                    remoteId = dto.id,
                    displayName = dto.displayName.ifBlank { dto.username },
                    handle = if ('@' in dto.acct) "@${dto.acct}" else "@${dto.acct}@$host",
                    avatarUrl = dto.avatar,
                    source = SourceKind.MASTODON,
                    accountLocalId = accountLocalId,
                    profileUrl = dto.url,
                    isBot = dto.bot,
                )
            }
            val next = LinkHeaderParser.parse(response.headers()["Link"]).next ?: break
            if (body.isEmpty()) break
            response = api.followingPage(bearer, next)
        }
        return result
    }

    private suspend fun loadMisskey(
        host: String,
        remoteUserId: String,
        token: String,
        accountLocalId: String,
    ): List<FollowedAccount> {
        val api = MisskeyClientFactory.create("https://$host")
        val result = mutableListOf<FollowedAccount>()
        var untilId: String? = null
        var guard = 0

        while (guard++ < MAX_PAGES) {
            val page = api.following(
                FollowingRequest(i = token, userId = remoteUserId, untilId = untilId),
            )
            if (page.isEmpty()) break
            page.forEach { entry ->
                val user = entry.followee ?: return@forEach
                result += FollowedAccount(
                    remoteId = user.id,
                    displayName = user.name?.takeIf { it.isNotBlank() } ?: user.username,
                    handle = "@${user.username}@${user.host ?: host}",
                    avatarUrl = user.avatarUrl,
                    source = SourceKind.MISSKEY,
                    accountLocalId = accountLocalId,
                    profileUrl = "https://$host/@${user.username}",
                    isBot = user.isBot,
                )
            }
            untilId = page.lastOrNull()?.id ?: break
        }
        return result
    }

    private companion object {
        /** Someone following 20k accounts should not lock the screen up forever. */
        const val MAX_PAGES = 40
    }
}
