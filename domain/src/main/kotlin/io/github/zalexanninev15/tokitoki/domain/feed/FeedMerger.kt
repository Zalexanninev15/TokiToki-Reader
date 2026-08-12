package io.github.zalexanninev15.tokitoki.domain.feed

import io.github.zalexanninev15.tokitoki.domain.model.FeedItem

/**
 * One source's contribution to the merged feed, with its own pagination state.
 *
 * Sources exhaust at wildly different depths: Telegram channel history is effectively
 * unbounded, while Misskey's `notes/timeline` only reaches back about 30 days. A single
 * shared offset across all sources would therefore either stall on the shallowest source
 * or silently drop the deepest one, so each carries its own cursor and [exhausted] flag.
 */
data class SourceWindow(
    val accountLocalId: String,
    val items: List<FeedItem>,
    val exhausted: Boolean,
)

data class MergedFeed(
    val items: List<FeedItem>,
    /**
     * Items newer than this are safe to display; older ones may still be superseded by a
     * source that has not been paged deep enough yet. Null when every source is exhausted.
     */
    val safeUntilEpochMillis: Long?,
    val sourcesNeedingMorePages: Set<String>,
)

object FeedMerger {

    /**
     * Merges windows newest-first.
     *
     * The subtle part is the watermark. If Mastodon has been paged back to Monday and
     * Misskey only to Wednesday, anything before Wednesday is incomplete — emitting it
     * would make Tuesday's Mastodon posts appear, then have Misskey posts inserted above
     * them on the next page load, which looks like the feed is rewriting itself. So the
     * merged list is cut at the newest "oldest item" among non-exhausted sources.
     */
    fun merge(windows: List<SourceWindow>, deduplicate: Boolean = true): MergedFeed {
        if (windows.isEmpty()) return MergedFeed(emptyList(), null, emptySet())

        val watermark = windows
            .filterNot { it.exhausted }
            .mapNotNull { window -> window.items.minOfOrNull { it.createdAtEpochMillis } }
            .maxOrNull()

        val all = windows.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { watermark == null || it.createdAtEpochMillis >= watermark }
            .sortedWith(
                compareByDescending<FeedItem> { it.createdAtEpochMillis }
                    .thenBy { it.id.value },
            )
            .toList()

        val items = if (deduplicate) dedupe(all) else all

        val needMore = windows
            .filterNot { it.exhausted }
            .filter { window ->
                watermark != null &&
                    window.items.none { it.createdAtEpochMillis <= watermark }
            }
            .map { it.accountLocalId }
            .toSet()

        return MergedFeed(items, watermark, needMore)
    }

    /**
     * Drops exact duplicates by id. Deliberately does NOT try to unify the same fediverse
     * post seen through two different accounts: the two copies have different remote ids,
     * different read-state, and collapsing them would break the read acknowledgement for
     * whichever account lost the coin toss.
     */
    private fun dedupe(items: List<FeedItem>): List<FeedItem> {
        val seen = HashSet<String>(items.size)
        return items.filter { seen.add(it.id.value) }
    }
}
