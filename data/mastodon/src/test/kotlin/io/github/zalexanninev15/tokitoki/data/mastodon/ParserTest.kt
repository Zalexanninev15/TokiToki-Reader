package io.github.zalexanninev15.tokitoki.data.mastodon

import io.github.zalexanninev15.tokitoki.data.mastodon.internal.LinkHeaderParser
import io.github.zalexanninev15.tokitoki.data.mastodon.internal.MastodonHtmlParser
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkHeaderParserTest {

    @Test
    fun `parses next and prev`() {
        val header = "<https://m.example/api/v1/timelines/home?max_id=109>; rel=\"next\", " +
            "<https://m.example/api/v1/timelines/home?min_id=113>; rel=\"prev\""
        val links = LinkHeaderParser.parse(header)
        assertEquals("https://m.example/api/v1/timelines/home?max_id=109", links.next)
        assertEquals("https://m.example/api/v1/timelines/home?min_id=113", links.prev)
    }

    @Test
    fun `handles a comma inside the url`() {
        val header = "<https://m.example/api?ids=1,2,3>; rel=\"next\""
        assertEquals("https://m.example/api?ids=1,2,3", LinkHeaderParser.parse(header).next)
    }

    @Test
    fun `handles unquoted rel and extra attributes`() {
        val header = "<https://m.example/x>; rel=next; type=application/json"
        assertEquals("https://m.example/x", LinkHeaderParser.parse(header).next)
    }

    @Test
    fun `missing header yields nothing`() {
        assertNull(LinkHeaderParser.parse(null).next)
        assertNull(LinkHeaderParser.parse("").next)
        assertNull(LinkHeaderParser.parse("garbage").next)
    }

    @Test
    fun `extracts the max_id cursor`() {
        val url = "https://m.example/api/v1/timelines/home?limit=40&max_id=109999"
        assertEquals("109999", LinkHeaderParser.queryParam(url, "max_id"))
        assertNull(LinkHeaderParser.queryParam(url, "min_id"))
    }

    @Test
    fun `decodes percent escapes in query params`() {
        val url = "https://m.example/api?q=hello%20world%21"
        assertEquals("hello world!", LinkHeaderParser.queryParam(url, "q"))
    }

    @Test
    fun `works with an instance behind a path prefix`() {
        val header = "<https://example.org/social/api/v1/timelines/home?max_id=5>; rel=\"next\""
        assertEquals("https://example.org/social/api/v1/timelines/home?max_id=5", LinkHeaderParser.parse(header).next)
    }
}

class MastodonHtmlParserTest {

    @Test
    fun `paragraphs become blank line separated text`() {
        val result = MastodonHtmlParser.parse("<p>first</p><p>second</p>")
        assertEquals("first\n\nsecond", result.plain)
    }

    @Test
    fun `br becomes a newline`() {
        assertEquals("a\nb", MastodonHtmlParser.parse("<p>a<br />b</p>").plain)
    }

    @Test
    fun `decodes html entities`() {
        assertEquals("a & b < c \"d\"", MastodonHtmlParser.parse("<p>a &amp; b &lt; c &quot;d&quot;</p>").plain)
    }

    @Test
    fun `decodes numeric entities`() {
        assertEquals("café ☕", MastodonHtmlParser.parse("<p>caf&#233; &#x2615;</p>").plain)
    }

    @Test
    fun `hides invisible spans used for url shortening`() {
        val html = """<p><a href="https://example.com/very/long/path" rel="nofollow">""" +
            """<span class="invisible">https://</span><span class="ellipsis">example.com/very</span>""" +
            """<span class="invisible">/long/path</span></a></p>"""
        val result = MastodonHtmlParser.parse(html)
        assertEquals("example.com/very", result.plain)
        val link = result.spans.single()
        assertEquals(SpanKind.LINK, link.kind)
        assertEquals("https://example.com/very/long/path", link.target)
    }

    @Test
    fun `distinguishes mention hashtag and plain link`() {
        val html = """<p><a href="https://x.social/@bob" class="u-url mention">@bob</a> """ +
            """<a href="https://x.social/tags/kotlin" class="mention hashtag" rel="tag">#kotlin</a> """ +
            """<a href="https://example.com">site</a></p>"""
        val kinds = MastodonHtmlParser.parse(html).spans.map { it.kind }
        assertEquals(listOf(SpanKind.MENTION, SpanKind.HASHTAG, SpanKind.LINK), kinds)
    }

    @Test
    fun `span offsets point at the right substring`() {
        val html = """<p>hello <a href="https://example.com">world</a></p>"""
        val result = MastodonHtmlParser.parse(html)
        val span = result.spans.single()
        assertEquals("world", result.plain.substring(span.start, span.end))
    }

    @Test
    fun `handles emphasis tags`() {
        val result = MastodonHtmlParser.parse("<p><strong>bold</strong> and <em>italic</em></p>")
        assertEquals("bold and italic", result.plain)
        assertEquals(listOf(SpanKind.BOLD, SpanKind.ITALIC), result.spans.map { it.kind })
    }

    @Test
    fun `unclosed tags do not throw`() {
        val result = MastodonHtmlParser.parse("<p>dangling <strong>bold")
        assertTrue(result.plain.startsWith("dangling bold"))
    }

    @Test
    fun `empty and null input`() {
        assertEquals("", MastodonHtmlParser.parse(null).plain)
        assertEquals("", MastodonHtmlParser.parse("").plain)
    }

    @Test
    fun `unknown tags are skipped but their text kept`() {
        assertEquals("keep me", MastodonHtmlParser.parse("<p><marquee>keep me</marquee></p>").plain)
    }

    @Test
    fun `all spans stay inside the plain text bounds`() {
        val html = "<p>a <strong>b</strong></p><p><em>c</em></p>"
        val result = MastodonHtmlParser.parse(html)
        result.spans.forEach {
            assertTrue(it.start >= 0 && it.end <= result.plain.length, "span $it out of bounds")
        }
    }
}
