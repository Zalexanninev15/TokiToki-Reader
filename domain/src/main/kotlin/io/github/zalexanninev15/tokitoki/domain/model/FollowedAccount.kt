package io.github.zalexanninev15.tokitoki.domain.model

/**
 * Someone the user follows, as shown in the subscriptions list.
 *
 * Deliberately smaller than [Author]: this list is a directory, not a feed, and carrying
 * the full author record would tempt callers into rendering posts from it.
 */
data class FollowedAccount(
    val remoteId: String,
    val displayName: String,
    val handle: String,
    val avatarUrl: String?,
    val source: SourceKind,
    val accountLocalId: String,
    val profileUrl: String?,
    val isBot: Boolean = false,
)
