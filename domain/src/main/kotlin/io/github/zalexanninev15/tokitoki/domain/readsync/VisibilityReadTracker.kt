package io.github.zalexanninev15.tokitoki.domain.readsync

import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId

/** Injectable clock so the dwell-time rules are testable without waiting in real time. */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

data class VisibilityRules(
    /** Fraction of the item's height that must be on screen to start the dwell timer. */
    val minVisibleFraction: Float = 0.6f,
    /** How long it must stay that visible before it counts as seen. */
    val minDwellMillis: Long = 1_500,
) {
    init {
        require(minVisibleFraction in 0f..1f) { "minVisibleFraction must be in 0..1" }
        require(minDwellMillis >= 0) { "minDwellMillis must be >= 0" }
    }
}

/**
 * Turns a stream of "this item is currently N% visible" reports into a set of items the
 * user genuinely looked at.
 *
 * The requirement this implements: loading 50 posts must not mark 50 posts as read.
 * Fast scrolling must not either — hence the dwell timer, which resets whenever the item
 * drops below the visibility threshold.
 *
 * Not thread-safe by design; drive it from a single UI-bound coroutine and hand the
 * emitted ids to the persistent queue.
 */
class VisibilityReadTracker(
    private val clock: MonotonicClock,
    private val rules: VisibilityRules = VisibilityRules(),
) {
    private val dwellStartedAt = HashMap<String, Long>()
    private val alreadyEmitted = HashSet<String>()

    /**
     * Report the current visibility of [id].
     *
     * @param visibleFraction 0f when off screen, 1f when fully on screen.
     * @return the id when it has just crossed into "seen", otherwise null.
     */
    fun report(id: FeedItemId, visibleFraction: Float): FeedItemId? {
        val key = id.value
        if (key in alreadyEmitted) return null

        if (visibleFraction < rules.minVisibleFraction) {
            dwellStartedAt.remove(key)
            return null
        }

        val now = clock.nowMillis()
        val startedAt = dwellStartedAt.getOrPut(key) { now }
        if (now - startedAt < rules.minDwellMillis) return null

        alreadyEmitted += key
        dwellStartedAt.remove(key)
        return id
    }

    /**
     * Opening the detail screen counts as seen immediately — the dwell rule exists to
     * filter out scroll-past, and an explicit tap is not scroll-past.
     */
    fun markOpened(id: FeedItemId): FeedItemId? =
        if (alreadyEmitted.add(id.value)) id else null

    /** Call when the feed is refreshed or the tab changes. */
    fun resetDwellTimers() = dwellStartedAt.clear()

    fun hasEmitted(id: FeedItemId): Boolean = id.value in alreadyEmitted
}
