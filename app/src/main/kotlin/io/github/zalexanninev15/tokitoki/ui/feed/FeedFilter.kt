package io.github.zalexanninev15.tokitoki.ui.feed

import io.github.zalexanninev15.tokitoki.domain.model.FeedItem

/**
 * Filtering applied to the already-cached feed.
 *
 * Deliberately local rather than a server search: Mastodon's `/api/v2/search` only finds
 * posts the instance has indexed, Misskey's `notes/search` is disabled on many instances,
 * and neither can search across accounts at once. Filtering what is already on the device
 * is predictable, instant, and works offline — which is what "search my feed" usually
 * means in a reader.
 */
data class FeedFilter(
    val query: String = "",
    val onlyWithMedia: Boolean = false,
    val onlyUnread: Boolean = false,
    val hideReposts: Boolean = false,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || onlyWithMedia || onlyUnread || hideReposts

    fun apply(items: List<FeedItem>, readIds: Set<String>): List<FeedItem> {
        if (!isActive) return items
        val needle = query.trim().lowercase()

        return items.filter { item ->
            val body = item.displayed
            val matchesQuery = needle.isEmpty() ||
                body.text.plain.lowercase().contains(needle) ||
                body.author.displayName.lowercase().contains(needle) ||
                body.author.handle.lowercase().contains(needle) ||
                body.contentWarning?.lowercase()?.contains(needle) == true

            val matchesMedia = !onlyWithMedia || item.hasMedia
            val matchesUnread = !onlyUnread || item.id.value !in readIds
            val matchesRepost = !hideReposts || item.reposted == null

            matchesQuery && matchesMedia && matchesUnread && matchesRepost
        }
    }
}
