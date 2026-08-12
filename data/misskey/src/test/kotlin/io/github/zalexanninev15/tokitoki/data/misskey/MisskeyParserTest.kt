package io.github.zalexanninev15.tokitoki.data.misskey

import io.github.zalexanninev15.tokitoki.data.misskey.internal.MfmParser
import io.github.zalexanninev15.tokitoki.data.misskey.internal.MisskeyFlavour
import io.github.zalexanninev15.tokitoki.data.misskey.internal.SemVer
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MfmParserTest {

    @Test
    fun `plain text passes through`() {
        assertEquals("just text", MfmParser.parse("just text").plain)
    }

    @Test
    fun `bold italic and strike`() {
        val result = MfmParser.parse("**b** *i* ~~s~~")
        assertEquals("b i s", result.plain)
        assertEquals(
            listOf(SpanKind.BOLD, SpanKind.ITALIC, SpanKind.STRIKETHROUGH),
            result.spans.map { it.kind },
        )
    }

    @Test
    fun `inline code keeps its content`() {
        val result = MfmParser.parse("run `git push` now")
        assertEquals("run git push now", result.plain)
        val span = result.spans.single()
        assertEquals(SpanKind.CODE, span.kind)
        assertEquals("git push", result.plain.substring(span.start, span.end))
    }

    @Test
    fun `animation functions are unwrapped not dropped`() {
        // $[jelly ...] would animate; the reader keeps the words and discards the effect.
        val result = MfmParser.parse("hello $[jelly world]")
        assertEquals("hello world", result.plain)
    }

    @Test
    fun `nested animation functions unwrap fully`() {
        assertEquals("deep", MfmParser.parse("$[spin $[jelly deep]]").plain)
    }

    @Test
    fun `function with options unwraps`() {
        assertEquals("colored", MfmParser.parse("$[fg.color=f00 colored]").plain)
    }

    @Test
    fun `markdown links carry the url`() {
        val result = MfmParser.parse("see [the docs](https://misskey-hub.net) please")
        assertEquals("see the docs please", result.plain)
        val span = result.spans.single { it.kind == SpanKind.LINK }
        assertEquals("https://misskey-hub.net", span.target)
        assertEquals("the docs", result.plain.substring(span.start, span.end))
    }

    @Test
    fun `bare urls are linkified without trailing punctuation`() {
        val result = MfmParser.parse("go to https://example.com/page, now")
        val span = result.spans.single { it.kind == SpanKind.LINK }
        assertEquals("https://example.com/page", span.target)
        assertTrue(result.plain.endsWith(", now"))
    }

    @Test
    fun `angle bracket urls`() {
        val result = MfmParser.parse("<https://example.com>")
        assertEquals("https://example.com", result.plain)
        assertEquals("https://example.com", result.spans.single().target)
    }

    @Test
    fun `remote and local mentions`() {
        val result = MfmParser.parse("@alice and @bob@misskey.io")
        val mentions = result.spans.filter { it.kind == SpanKind.MENTION }.map { it.target }
        assertEquals(listOf("alice", "bob@misskey.io"), mentions)
    }

    @Test
    fun `hashtags are recognised but bare numbers are not`() {
        val result = MfmParser.parse("#kotlin #123")
        val tags = result.spans.filter { it.kind == SpanKind.HASHTAG }.map { it.target }
        assertEquals(listOf("kotlin"), tags)
    }

    @Test
    fun `custom emoji shortcodes are marked`() {
        val result = MfmParser.parse("nice :blobcat: one")
        val emoji = result.spans.single { it.kind == SpanKind.CUSTOM_EMOJI }
        assertEquals("blobcat", emoji.target)
        assertEquals(":blobcat:", result.plain.substring(emoji.start, emoji.end))
    }

    @Test
    fun `a lone colon is not an emoji`() {
        val result = MfmParser.parse("time: 12:00 sharp")
        assertTrue(result.spans.none { it.kind == SpanKind.CUSTOM_EMOJI })
    }

    @Test
    fun `quote lines`() {
        val result = MfmParser.parse("> quoted\nnormal")
        assertTrue(result.spans.any { it.kind == SpanKind.QUOTE })
        assertTrue(result.plain.startsWith("quoted"))
    }

    @Test
    fun `unterminated markers stay literal`() {
        assertEquals("**not bold", MfmParser.parse("**not bold").plain)
        assertEquals("`unclosed", MfmParser.parse("`unclosed").plain)
    }

    @Test
    fun `empty input`() {
        assertEquals("", MfmParser.parse(null).plain)
        assertEquals("", MfmParser.parse("").plain)
    }

    @Test
    fun `all spans stay inside bounds`() {
        val result = MfmParser.parse("**b** [x](https://y.z) @a #t :e: `c`")
        result.spans.forEach {
            assertTrue(it.start >= 0 && it.end <= result.plain.length, "out of bounds: $it")
        }
    }
}

class MisskeyFlavourTest {

    @Test
    fun `modern misskey uses miauth and supports oauth`() {
        val flavour = MisskeyFlavour.from("misskey", "2024.5.0")
        assertEquals(MisskeyFlavour.AuthMechanism.MI_AUTH, flavour.authMechanism)
        assertTrue(flavour.supportsOAuth)
    }

    @Test
    fun `pre oauth misskey still uses miauth`() {
        val flavour = MisskeyFlavour.from("misskey", "13.14.2")
        assertEquals(MisskeyFlavour.AuthMechanism.MI_AUTH, flavour.authMechanism)
        assertFalse(flavour.supportsOAuth)
    }

    @Test
    fun `ancient servers fall back to the legacy app flow`() {
        val flavour = MisskeyFlavour.from("misskey", "11.37.1")
        assertEquals(MisskeyFlavour.AuthMechanism.LEGACY_APP, flavour.authMechanism)
    }

    @Test
    fun `forks are recognised as the misskey family`() {
        assertTrue(MisskeyFlavour.isMisskeyFamily("Sharkey"))
        assertTrue(MisskeyFlavour.isMisskeyFamily("iceshrimp"))
        assertFalse(MisskeyFlavour.isMisskeyFamily("mastodon"))
        assertFalse(MisskeyFlavour.isMisskeyFamily(null))
    }

    @Test
    fun `forks do not claim oauth support`() {
        assertFalse(MisskeyFlavour.from("sharkey", "2024.5.0-sharkey.1").supportsOAuth)
    }

    @Test
    fun `unknown version is treated optimistically`() {
        assertEquals(MisskeyFlavour.AuthMechanism.MI_AUTH, MisskeyFlavour.from("misskey", null).authMechanism)
    }
}

class SemVerTest {

    @Test
    fun `parses calendar versions`() {
        assertEquals(SemVer(2024, 5, 0), SemVer.parse("2024.5.0"))
    }

    @Test
    fun `strips fork and prerelease suffixes`() {
        assertEquals(SemVer(2024, 5, 0), SemVer.parse("2024.5.0-sharkey.1"))
        assertEquals(SemVer(12, 119, 2), SemVer.parse("12.119.2-beta.3"))
    }

    @Test
    fun `handles a bare major version`() {
        assertEquals(SemVer(13, 0, 0), SemVer.parse("13"))
    }

    @Test
    fun `rejects junk`() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("unknown"))
    }

    @Test
    fun `orders correctly`() {
        assertTrue(SemVer(2024, 5, 0) > SemVer(13, 14, 2))
        assertTrue(SemVer(12, 0, 0) > SemVer(11, 37, 1))
    }
}
