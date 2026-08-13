package io.github.zalexanninev15.tokitoki.domain.model

/**
 * Whether the signed-in account has interacted with a post, and the public counters.
 *
 * Nullable on [FeedItem] because Telegram channel messages have no such concept, and a
 * default of "not favourited" would be a lie rather than an absence.
 */
data class PostInteractions(
    val favourited: Boolean = false,
    val boosted: Boolean = false,
    val favouriteCount: Int = 0,
    val boostCount: Int = 0,
    val replyCount: Int = 0,
    /**
     * Misskey allows any emoji as a reaction, so "did I react" is a shortcode rather than
     * a flag. Mastodon has only the single star, represented here as "⭐" when set.
     */
    val myReaction: String? = null,
) {
    val hasReacted: Boolean get() = favourited || myReaction != null
}

/** What the composer is doing, which decides the request the repository sends. */
sealed interface ComposeTarget {
    /** Which connected account is publishing. Every variant carries one. */
    val accountLocalId: String

    data class NewPost(override val accountLocalId: String) : ComposeTarget

    data class Reply(
        override val accountLocalId: String,
        val inReplyToRemoteId: String,
        val inReplyToHandle: String,
    ) : ComposeTarget

    /** A boost carrying commentary: a quote on Misskey, a plain post elsewhere. */
    data class Quote(
        override val accountLocalId: String,
        val quotedRemoteId: String,
        val quotedUrl: String?,
    ) : ComposeTarget
}

/**
 * Who can see a post. The two services name these differently but the ladder is the same;
 * mapping happens in each data module rather than leaking service vocabulary here.
 */
enum class PostVisibility { PUBLIC, UNLISTED, FOLLOWERS, DIRECT }
