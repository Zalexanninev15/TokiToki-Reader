package io.github.zalexanninev15.tokitoki.data.repo

import io.github.zalexanninev15.tokitoki.data.db.AccountDao
import io.github.zalexanninev15.tokitoki.data.db.FeedDao
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.dto.CreateNoteRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.NoteIdRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.ReactionRequest
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.domain.model.ComposeTarget
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.PostVisibility
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/**
 * Everything that writes to a server: publishing, replying, boosting, reacting.
 *
 * Kept apart from FeedRepository on purpose. Reads are cached, batched and retried in the
 * background; writes are one-shot, user-initiated and must report their outcome
 * immediately. Mixing the two would mean a failed boost quietly joining a retry queue,
 * which is not what a tap on a button should do.
 */
class PostActionsRepository(
    private val accountDao: AccountDao,
    private val feedDao: FeedDao,
    private val secureStore: SecureStore,
) {

    suspend fun publish(
        target: ComposeTarget,
        text: String,
        contentWarning: String?,
        visibility: PostVisibility,
    ): Result<Unit> = runAction(target.accountLocalId) { source, host, token ->
        when (source) {
            SourceKind.MASTODON -> {
                val api = MastodonClientFactory.create("https://$host")
                val status = when (target) {
                    is ComposeTarget.Quote ->
                        // Mastodon has no quote API, so the quoted link goes in the body.
                        listOfNotNull(text.takeIf { it.isNotBlank() }, target.quotedUrl)
                            .joinToString("\n\n")
                    else -> text
                }
                api.postStatus(
                    bearer = "Bearer $token",
                    idempotencyKey = UUID.randomUUID().toString(),
                    status = status,
                    inReplyToId = (target as? ComposeTarget.Reply)?.inReplyToRemoteId,
                    spoilerText = contentWarning,
                    visibility = visibility.mastodon(),
                )
            }

            SourceKind.MISSKEY -> {
                val api = MisskeyClientFactory.create("https://$host")
                api.createNote(
                    CreateNoteRequest(
                        i = token,
                        text = text.takeIf { it.isNotBlank() },
                        cw = contentWarning,
                        replyId = (target as? ComposeTarget.Reply)?.inReplyToRemoteId,
                        renoteId = (target as? ComposeTarget.Quote)?.quotedRemoteId,
                        visibility = visibility.misskey(),
                    ),
                )
            }

            SourceKind.TELEGRAM -> throw SourceError.Unsupported("telegram posting")
        }
    }

    /** Star on Mastodon, emoji reaction on Misskey. [reaction] is ignored by Mastodon. */
    suspend fun toggleReaction(
        id: FeedItemId,
        currentlyReacted: Boolean,
        reaction: String = DEFAULT_REACTION,
    ): Result<Unit> = runAction(id.accountLocalId) { source, host, token ->
        when (source) {
            SourceKind.MASTODON -> {
                val api = MastodonClientFactory.create("https://$host")
                val bearer = "Bearer $token"
                if (currentlyReacted) api.unfavourite(bearer, id.remoteId)
                else api.favourite(bearer, id.remoteId)
            }

            SourceKind.MISSKEY -> {
                val api = MisskeyClientFactory.create("https://$host")
                if (currentlyReacted) {
                    api.deleteReaction(NoteIdRequest(token, id.remoteId))
                } else {
                    api.createReaction(ReactionRequest(token, id.remoteId, reaction))
                }
            }

            SourceKind.TELEGRAM -> throw SourceError.Unsupported("telegram reactions")
        }
    }

    suspend fun toggleBoost(id: FeedItemId, currentlyBoosted: Boolean): Result<Unit> =
        runAction(id.accountLocalId) { source, host, token ->
            when (source) {
                SourceKind.MASTODON -> {
                    val api = MastodonClientFactory.create("https://$host")
                    val bearer = "Bearer $token"
                    if (currentlyBoosted) api.unreblog(bearer, id.remoteId)
                    else api.reblog(bearer, id.remoteId)
                }

                SourceKind.MISSKEY -> {
                    // A renote is a note with only renoteId set.
                    val api = MisskeyClientFactory.create("https://$host")
                    api.createNote(CreateNoteRequest(i = token, renoteId = id.remoteId))
                }

                SourceKind.TELEGRAM -> throw SourceError.Unsupported("telegram boosts")
            }
        }

    private suspend fun runAction(
        accountLocalId: String,
        block: suspend (SourceKind, String, String) -> Unit,
    ): Result<Unit> = runCatching {
        val account = accountDao.byId(accountLocalId)
            ?: throw SourceError.NotFound("account $accountLocalId")
        val token = secureStore.token(accountLocalId)
            ?: throw SourceError.Unauthorized(accountLocalId)
        block(SourceKind.valueOf(account.source), account.host, token)
    }.recoverCatching { cause ->
        throw when (cause) {
            is HttpException -> when (cause.code()) {
                401 -> SourceError.Unauthorized(accountLocalId)
                // Tokens issued before this app asked for write scopes are still valid
                // for reading, so the user sees a 403 rather than a login prompt.
                403 -> SourceError.Forbidden("missing write permission — sign in again")
                404 -> SourceError.NotFound("post no longer exists")
                429 -> SourceError.RateLimited(null)
                else -> SourceError.Server(cause.code(), cause.message())
            }
            is IOException -> SourceError.Network(cause)
            else -> cause
        }
    }

    private companion object {
        const val DEFAULT_REACTION = "\uD83D\uDC4D"
    }
}

private fun PostVisibility.mastodon(): String = when (this) {
    PostVisibility.PUBLIC -> "public"
    PostVisibility.UNLISTED -> "unlisted"
    PostVisibility.FOLLOWERS -> "private"
    PostVisibility.DIRECT -> "direct"
}

private fun PostVisibility.misskey(): String = when (this) {
    PostVisibility.PUBLIC -> "public"
    PostVisibility.UNLISTED -> "home"
    PostVisibility.FOLLOWERS -> "followers"
    PostVisibility.DIRECT -> "specified"
}
