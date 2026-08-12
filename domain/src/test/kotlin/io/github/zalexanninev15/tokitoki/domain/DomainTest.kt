package io.github.zalexanninev15.tokitoki.domain

import io.github.zalexanninev15.tokitoki.domain.feed.FeedMerger
import io.github.zalexanninev15.tokitoki.domain.feed.SourceWindow
import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.readsync.BackoffPolicy
import io.github.zalexanninev15.tokitoki.domain.readsync.MonotonicClock
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncJob
import io.github.zalexanninev15.tokitoki.domain.readsync.ReadSyncOutcome
import io.github.zalexanninev15.tokitoki.domain.readsync.TimelineCursorPolicy
import io.github.zalexanninev15.tokitoki.domain.readsync.VisibilityReadTracker
import io.github.zalexanninev15.tokitoki.domain.readsync.VisibilityRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClock(var now: Long = 0) : MonotonicClock {
    override fun nowMillis(): Long = now
}

private fun item(id: String, millis: Long, account: String = "acc1"): FeedItem = FeedItem(
    id = FeedItemId(SourceKind.MASTODON, account, id),
    author = Author("a", "A", "@a@example.social", null),
    createdAtEpochMillis = millis,
    text = RichText.plain("body $id"),
)

class TimelineCursorPolicyTest {

    @Test
    fun `picks the highest numeric id`() {
        assertEquals("103", TimelineCursorPolicy.advance(null, listOf("99", "103", "7")))
    }

    @Test
    fun `orders numeric ids by magnitude not lexicographically`() {
        // The classic bug: "9" > "10" under a plain string compare.
        assertEquals("10", TimelineCursorPolicy.advance(null, listOf("9", "10")))
    }

    @Test
    fun `never moves the cursor backwards`() {
        assertNull(TimelineCursorPolicy.advance("500", listOf("100", "499")))
    }

    @Test
    fun `moves forward only past the current position`() {
        assertEquals("501", TimelineCursorPolicy.advance("500", listOf("400", "501")))
    }

    @Test
    fun `handles non numeric flake ids without throwing`() {
        // Pleroma and Akkoma use base-62 FlakeIds; toLong() would blow up here.
        val result = TimelineCursorPolicy.advance(null, listOf("9iF4bT2xQ", "9iF4bT2xR"))
        assertEquals("9iF4bT2xR", result)
    }

    @Test
    fun `ignores empty ids`() {
        assertEquals("42", TimelineCursorPolicy.advance(null, listOf("", "42", "")))
        assertNull(TimelineCursorPolicy.advance(null, listOf("", "")))
    }

    @Test
    fun `isCoveredBy is inclusive of the cursor itself`() {
        assertTrue(TimelineCursorPolicy.isCoveredBy("100", "100"))
        assertTrue(TimelineCursorPolicy.isCoveredBy("99", "100"))
        assertFalse(TimelineCursorPolicy.isCoveredBy("101", "100"))
        assertFalse(TimelineCursorPolicy.isCoveredBy("101", null))
    }

    @Test
    fun `newest picks the furthest cursor and ignores nulls`() {
        assertEquals("500", TimelineCursorPolicy.newest("100", null, "500", ""))
        assertEquals("100", TimelineCursorPolicy.newest("100"))
        assertNull(TimelineCursorPolicy.newest(null, null))
        assertNull(TimelineCursorPolicy.newest())
    }

    @Test
    fun `newest compares numerically not lexicographically`() {
        assertEquals("100", TimelineCursorPolicy.newest("99", "100"))
    }
}

class VisibilityReadTrackerTest {

    @Test
    fun `barely visible item never counts`() {
        val clock = FakeClock()
        val tracker = VisibilityReadTracker(clock)
        val id = item("1", 0).id
        assertNull(tracker.report(id, 0.3f))
        clock.now += 10_000
        assertNull(tracker.report(id, 0.3f))
    }

    @Test
    fun `visible item counts only after the dwell time`() {
        val clock = FakeClock()
        val tracker = VisibilityReadTracker(clock, VisibilityRules(0.6f, 1_500))
        val id = item("1", 0).id

        assertNull(tracker.report(id, 0.9f))
        clock.now += 1_400
        assertNull(tracker.report(id, 0.9f))
        clock.now += 200
        assertEquals(id, tracker.report(id, 0.9f))
    }

    @Test
    fun `fast scrolling past resets the timer`() {
        val clock = FakeClock()
        val tracker = VisibilityReadTracker(clock, VisibilityRules(0.6f, 1_500))
        val id = item("1", 0).id

        tracker.report(id, 0.9f)
        clock.now += 1_000
        tracker.report(id, 0.0f) // scrolled away before the threshold
        clock.now += 1_000
        assertNull(tracker.report(id, 0.9f)) // timer restarted, not resumed
    }

    @Test
    fun `an item is emitted at most once`() {
        val clock = FakeClock()
        val tracker = VisibilityReadTracker(clock, VisibilityRules(0.6f, 0))
        val id = item("1", 0).id
        assertEquals(id, tracker.report(id, 1f))
        assertNull(tracker.report(id, 1f))
    }

    @Test
    fun `loading fifty posts and showing five marks five`() {
        val clock = FakeClock()
        val tracker = VisibilityReadTracker(clock, VisibilityRules(0.6f, 1_500))
        val loaded = (1..50).map { item("$it", it.toLong()).id }

        val emitted = buildList {
            repeat(2) {
                clock.now += 1_600
                loaded.take(5).forEach { id -> tracker.report(id, 1f)?.let(::add) }
                loaded.drop(5).forEach { id -> tracker.report(id, 0f) }
            }
        }

        assertEquals(5, emitted.size)
    }

    @Test
    fun `opening the detail screen bypasses the dwell rule`() {
        val tracker = VisibilityReadTracker(FakeClock(), VisibilityRules(0.6f, 10_000))
        val id = item("1", 0).id
        assertEquals(id, tracker.markOpened(id))
        assertNull(tracker.markOpened(id))
        assertTrue(tracker.hasEmitted(id))
    }
}

class BackoffPolicyTest {

    private val noJitter = BackoffPolicy(baseDelayMillis = 1_000, maxAttempts = 4, jitter = { it })

    @Test
    fun `delay grows exponentially and is capped`() {
        assertEquals(1_000, noJitter.nextDelayMillis(0))
        assertEquals(2_000, noJitter.nextDelayMillis(1))
        assertEquals(4_000, noJitter.nextDelayMillis(2))
        assertEquals(30 * 60 * 1_000L, noJitter.nextDelayMillis(60))
    }

    @Test
    fun `success removes the job`() {
        val job = ReadSyncJob(item("1", 0).id, 0)
        assertNull(noJitter.apply(job, ReadSyncOutcome.Success, 0))
    }

    @Test
    fun `permanent failure does not retry`() {
        val job = ReadSyncJob(item("1", 0).id, 0)
        assertNull(noJitter.apply(job, ReadSyncOutcome.Permanent("401"), 0))
    }

    @Test
    fun `transient failure reschedules and gives up eventually`() {
        var job: ReadSyncJob? = ReadSyncJob(item("1", 0).id, 0)
        val retry = ReadSyncOutcome.Transient("500")

        job = noJitter.apply(job!!, retry, 0)
        assertNotNull(job)
        assertEquals(1, job.attempts)
        assertEquals(2_000, job.nextAttemptAtMillis)

        repeat(3) { job = job?.let { noJitter.apply(it, retry, 0) } }
        assertNull(job)
    }

    @Test
    fun `server supplied retry after wins over the computed delay`() {
        val job = ReadSyncJob(item("1", 0).id, 0)
        val updated = noJitter.apply(job, ReadSyncOutcome.Transient("429", 90_000), 1_000)
        assertEquals(91_000, updated?.nextAttemptAtMillis)
    }
}

class FeedMergerTest {

    @Test
    fun `merges newest first`() {
        val merged = FeedMerger.merge(
            listOf(
                SourceWindow("a", listOf(item("1", 300), item("2", 100)), exhausted = true),
                SourceWindow("b", listOf(item("3", 200, "b")), exhausted = true),
            ),
        )
        assertEquals(listOf("1", "3", "2"), merged.items.map { it.id.remoteId })
    }

    @Test
    fun `cuts at the shallowest source so the feed does not rewrite itself`() {
        // Mastodon paged back to t=100, Misskey only to t=250. Anything below 250 is
        // incomplete and must not be shown yet.
        val merged = FeedMerger.merge(
            listOf(
                SourceWindow("mastodon", listOf(item("1", 400), item("2", 100)), exhausted = false),
                SourceWindow("misskey", listOf(item("3", 300, "misskey")), exhausted = false),
            ),
        )
        assertEquals(300L, merged.safeUntilEpochMillis)
        assertEquals(listOf("1", "3"), merged.items.map { it.id.remoteId })
    }

    @Test
    fun `exhausted sources do not hold back the watermark`() {
        val merged = FeedMerger.merge(
            listOf(
                SourceWindow("deep", listOf(item("1", 400), item("2", 100)), exhausted = false),
                SourceWindow("shallow", listOf(item("3", 350, "shallow")), exhausted = true),
            ),
        )
        assertEquals(100L, merged.safeUntilEpochMillis)
        assertEquals(3, merged.items.size)
    }

    @Test
    fun `all sources exhausted means no watermark`() {
        val merged = FeedMerger.merge(
            listOf(SourceWindow("a", listOf(item("1", 400)), exhausted = true)),
        )
        assertNull(merged.safeUntilEpochMillis)
    }

    @Test
    fun `duplicate ids are collapsed`() {
        val merged = FeedMerger.merge(
            listOf(
                SourceWindow("a", listOf(item("1", 400)), exhausted = true),
                SourceWindow("a", listOf(item("1", 400)), exhausted = true),
            ),
        )
        assertEquals(1, merged.items.size)
    }

    @Test
    fun `empty input is handled`() {
        val merged = FeedMerger.merge(emptyList())
        assertTrue(merged.items.isEmpty())
        assertNull(merged.safeUntilEpochMillis)
    }

    @Test
    fun `equal timestamps get a stable deterministic order`() {
        val a = FeedMerger.merge(
            listOf(SourceWindow("a", listOf(item("2", 100), item("1", 100)), exhausted = true)),
        )
        val b = FeedMerger.merge(
            listOf(SourceWindow("a", listOf(item("1", 100), item("2", 100)), exhausted = true)),
        )
        assertEquals(a.items.map { it.id.value }, b.items.map { it.id.value })
    }
}

class FeedItemIdTest {

    @Test
    fun `round trips through its string form`() {
        val id = FeedItemId(SourceKind.MISSKEY, "acc-7", "9abc")
        assertEquals(id, FeedItemId.parse(id.value))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(FeedItemId.parse("nonsense"))
        assertNull(FeedItemId.parse("FACEBOOK|a|b"))
    }

    @Test
    fun `remote ids do not collide across accounts`() {
        val a = FeedItemId(SourceKind.MASTODON, "acc1", "123")
        val b = FeedItemId(SourceKind.MASTODON, "acc2", "123")
        assertTrue(a.value != b.value)
    }
}
