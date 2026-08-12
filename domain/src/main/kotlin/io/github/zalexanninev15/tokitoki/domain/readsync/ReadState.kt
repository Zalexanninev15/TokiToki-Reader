package io.github.zalexanninev15.tokitoki.domain.readsync

import io.github.zalexanninev15.tokitoki.domain.model.SourceKind

/**
 * How faithfully a backend can represent "the user has seen this".
 *
 * This is the reason there is no plain `isRead: Boolean` on FeedItem: the three
 * supported services sit at three different levels and the UI has to say so.
 */
sealed interface ReadSyncCapability {

    /** Per-message acknowledgement, identical to the official client. Telegram via TDLib. */
    data object PerMessage : ReadSyncCapability

    /**
     * A single "everything up to this id" cursor for the whole timeline.
     * Mastodon `POST /api/v1/markers`. Cannot express "read #5 but not #3".
     */
    data object TimelineCursor : ReadSyncCapability

    /**
     * The server has no timeline read-state at all; the flag lives only on this device.
     * Misskey. [scopes] lists the partial mechanisms that DO exist, if any.
     */
    data class LocalOnly(val scopes: Set<PartialScope> = emptySet()) : ReadSyncCapability

    enum class PartialScope { NOTIFICATIONS, MENTIONS, ANTENNA, CHANNEL }

    companion object {
        /**
         * Default capability per source. Mastodon-compatible servers (Pleroma, Akkoma,
         * GoToSocial) may not implement markers, so the repository downgrades to
         * [LocalOnly] on a 404 rather than treating it as a failure.
         */
        fun default(source: SourceKind): ReadSyncCapability = when (source) {
            SourceKind.TELEGRAM -> PerMessage
            SourceKind.MASTODON -> TimelineCursor
            SourceKind.MISSKEY -> LocalOnly(setOf(PartialScope.NOTIFICATIONS, PartialScope.MENTIONS))
        }
    }
}

/**
 * Read state of a single item. [remoteConfirmed] is deliberately separate from
 * [locallyRead] so the UI can distinguish "we know the server agrees" from
 * "we marked it here and hope for the best".
 */
data class ReadState(
    val locallyRead: Boolean,
    val remoteConfirmed: Boolean,
    val capability: ReadSyncCapability,
) {
    /** True when the checkmark shown to the user is backed by the origin server. */
    val isTrustworthy: Boolean
        get() = remoteConfirmed || capability is ReadSyncCapability.LocalOnly && !locallyRead

    companion object {
        fun unread(capability: ReadSyncCapability) = ReadState(false, false, capability)
    }
}

/**
 * Advancing a Mastodon-style timeline cursor.
 *
 * Two rules that third-party clients routinely get wrong:
 *  1. The cursor must never move backwards. Scrolling down into older posts must not
 *     clobber a newer position set by the web client on another device.
 *  2. Ids are not always numeric. Mastodon uses snowflakes, but Pleroma and Akkoma use
 *     base-62 FlakeIds, so a naive `toLong()` throws and a naive string compare puts
 *     "9" above "10". Compare by length first, then lexicographically.
 */
object TimelineCursorPolicy {

    /**
     * @return the id the cursor should be moved to, or null when it must not move.
     */
    fun advance(current: String?, seen: Collection<String>): String? {
        val best = seen.filter { it.isNotEmpty() }.maxWithOrNull(ID_ORDER) ?: return null
        if (current.isNullOrEmpty()) return best
        return if (ID_ORDER.compare(best, current) > 0) best else null
    }

    /** True when [candidate] is at or before [cursor], i.e. already covered by it. */
    fun isCoveredBy(candidate: String, cursor: String?): Boolean {
        if (cursor.isNullOrEmpty()) return false
        return ID_ORDER.compare(candidate, cursor) <= 0
    }

    /**
     * The furthest-along of several cursors, ignoring nulls.
     *
     * Exists so callers in other modules can reconcile a stored cursor with the one the
     * server reports without reaching for the comparator itself: `internal` is scoped to
     * the Gradle module, so exposing the ordering as a helper keeps the comparison rules
     * in one place instead of being reimplemented per data source.
     */
    fun newest(vararg candidates: String?): String? =
        candidates.filterNotNull().filter { it.isNotEmpty() }.maxWithOrNull(ID_ORDER)

    internal val ID_ORDER: Comparator<String> = Comparator { a, b ->
        val aNum = a.all { it in '0'..'9' }
        val bNum = b.all { it in '0'..'9' }
        when {
            aNum && bNum -> {
                val byLength = a.trimStart('0').length.compareTo(b.trimStart('0').length)
                if (byLength != 0) byLength else a.compareTo(b)
            }
            // Mixed alphabets cannot be ordered meaningfully; fall back to a total order
            // so the comparator stays consistent, and prefer the longer id.
            else -> {
                val byLength = a.length.compareTo(b.length)
                if (byLength != 0) byLength else a.compareTo(b)
            }
        }
    }
}
