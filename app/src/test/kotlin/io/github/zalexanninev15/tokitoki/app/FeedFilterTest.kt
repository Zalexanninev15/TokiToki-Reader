package io.github.zalexanninev15.tokitoki.app

import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.MediaAttachment
import io.github.zalexanninev15.tokitoki.domain.model.MediaKind
import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.ui.feed.FeedFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun item(
    id: String,
    text: String = "",
    name: String = "Alice",
    handle: String = "@alice@example.social",
    media: Boolean = false,
    repost: Boolean = false,
    cw: String? = null,
): FeedItem {
    val base = FeedItem(
        id = FeedItemId(SourceKind.MASTODON, "acc", id),
        author = Author("a", name, handle, null),
        createdAtEpochMillis = id.toLong(),
        text = RichText.plain(text),
        media = if (media) listOf(MediaAttachment(MediaKind.IMAGE, "u", null, null, 1, 1)) else emptyList(),
        contentWarning = cw,
    )
    return if (repost) base.copy(reposted = base.copy(), repostedBy = base.author) else base
}

class FeedFilterTest {

    private val items = listOf(
        item("1", text = "Kotlin coroutines are neat"),
        item("2", text = "lunch photo", media = true),
        item("3", text = "boosted thing", repost = true),
        item("4", text = "spoiler inside", cw = "Politics"),
        item("5", text = "hello", name = "Bob", handle = "@bob@misskey.io"),
    )

    @Test
    fun `inactive filter returns everything untouched`() {
        val filter = FeedFilter()
        assertFalse(filter.isActive)
        assertEquals(items, filter.apply(items, emptySet()))
    }

    @Test
    fun `query matches post text case insensitively`() {
        val result = FeedFilter(query = "KOTLIN").apply(items, emptySet())
        assertEquals(listOf("1"), result.map { it.id.remoteId })
    }

    @Test
    fun `query matches display name and handle`() {
        assertEquals(listOf("5"), FeedFilter(query = "bob").apply(items, emptySet()).map { it.id.remoteId })
        assertEquals(listOf("5"), FeedFilter(query = "misskey.io").apply(items, emptySet()).map { it.id.remoteId })
    }

    @Test
    fun `query matches the content warning`() {
        assertEquals(listOf("4"), FeedFilter(query = "politics").apply(items, emptySet()).map { it.id.remoteId })
    }

    @Test
    fun `media filter keeps only posts with attachments`() {
        assertEquals(listOf("2"), FeedFilter(onlyWithMedia = true).apply(items, emptySet()).map { it.id.remoteId })
    }

    @Test
    fun `unread filter drops what has been read`() {
        val read = setOf(items[0].id.value, items[1].id.value)
        val result = FeedFilter(onlyUnread = true).apply(items, read)
        assertEquals(listOf("3", "4", "5"), result.map { it.id.remoteId })
    }

    @Test
    fun `repost filter drops boosts`() {
        val result = FeedFilter(hideReposts = true).apply(items, emptySet())
        assertFalse(result.any { it.id.remoteId == "3" })
    }

    @Test
    fun `filters combine`() {
        val result = FeedFilter(query = "photo", onlyWithMedia = true).apply(items, emptySet())
        assertEquals(listOf("2"), result.map { it.id.remoteId })
    }

    @Test
    fun `blank query does not activate the filter`() {
        assertFalse(FeedFilter(query = "   ").isActive)
    }

    @Test
    fun `query searching a boost looks at the boosted body`() {
        val result = FeedFilter(query = "boosted").apply(items, emptySet())
        assertTrue(result.any { it.id.remoteId == "3" })
    }
}
