package io.github.zalexanninev15.tokitoki.data.misskey.internal

import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind
import io.github.zalexanninev15.tokitoki.domain.model.TextSpan

/**
 * Parses MFM (Misskey Flavoured Markdown) into the app-wide [RichText].
 *
 * MFM is not HTML and not CommonMark, so neither the Mastodon path nor a Markdown library
 * can be reused. Supported here:
 *
 *  - `**bold**`, `*italic*`, `~~strike~~`, `` `code` ``, ```` ```block``` ````
 *  - `> quote` lines
 *  - `[label](url)`, `<https://url>`, bare URLs
 *  - `@user` and `@user@host` mentions, `#hashtag`
 *  - `:shortcode:` custom emoji
 *  - `$[fx.opt=v content]` animation functions — the wrapper is stripped and the inner
 *    content parsed, since animating text in a feed reader is noise, not information
 *
 * Not supported, by choice: `<center>`, `$[sparkle]` particle effects, and search blocks.
 * They degrade to their literal inner text rather than being dropped.
 */
object MfmParser {

    private const val MAX_DEPTH = 8

    fun parse(source: String?): RichText {
        if (source.isNullOrEmpty()) return RichText.EMPTY
        val out = StringBuilder()
        val spans = mutableListOf<TextSpan>()
        parseInto(source, out, spans, 0)
        return RichText(out.toString(), spans.sortedBy { it.start })
    }

    private fun parseInto(source: String, out: StringBuilder, spans: MutableList<TextSpan>, depth: Int) {
        if (depth > MAX_DEPTH) { out.append(source); return }

        var i = 0
        var atLineStart = true

        while (i < source.length) {
            val rest = source.substring(i)

            // Fenced code block
            if (atLineStart && rest.startsWith("```")) {
                val close = source.indexOf("```", i + 3)
                if (close > 0) {
                    val body = source.substring(i + 3, close).trim('\n')
                    val start = out.length
                    out.append(body.substringAfter('\n', body))
                    spans += TextSpan(start, out.length, SpanKind.CODE)
                    i = close + 3
                    atLineStart = false
                    continue
                }
            }

            // Quote line
            if (atLineStart && (rest.startsWith("> ") || rest.startsWith(">"))) {
                val lineEnd = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                val body = source.substring(i, lineEnd).removePrefix(">").removePrefix(" ")
                val start = out.length
                parseInto(body, out, spans, depth + 1)
                spans += TextSpan(start, out.length, SpanKind.QUOTE)
                i = lineEnd
                continue
            }

            val consumed = when {
                rest.startsWith("$[") -> parseFunction(rest, out, spans, depth)
                rest.startsWith("**") -> parseDelimited(rest, "**", SpanKind.BOLD, out, spans, depth)
                rest.startsWith("~~") -> parseDelimited(rest, "~~", SpanKind.STRIKETHROUGH, out, spans, depth)
                rest.startsWith("*") -> parseDelimited(rest, "*", SpanKind.ITALIC, out, spans, depth)
                rest.startsWith("`") -> parseCode(rest, out, spans)
                rest.startsWith("[") -> parseMarkdownLink(rest, out, spans, depth)
                rest.startsWith("<http") -> parseAngleUrl(rest, out, spans)
                rest.startsWith("http://") || rest.startsWith("https://") -> parseBareUrl(rest, out, spans)
                rest.startsWith("@") -> parseMention(rest, out, spans)
                rest.startsWith("#") -> parseHashtag(rest, out, spans)
                rest.startsWith(":") -> parseEmoji(rest, out, spans)
                else -> 0
            }

            if (consumed > 0) {
                i += consumed
                atLineStart = false
            } else {
                val c = source[i]
                out.append(c)
                atLineStart = c == '\n'
                i++
            }
        }
    }

    private fun parseFunction(rest: String, out: StringBuilder, spans: MutableList<TextSpan>, depth: Int): Int {
        val nameEnd = rest.indexOfFirst { it == ' ' }
        if (nameEnd < 0) return 0
        var level = 0
        var end = -1
        for (j in 1 until rest.length) {
            when (rest[j]) {
                '[' -> level++
                ']' -> { level--; if (level == 0) { end = j; break } }
            }
        }
        if (end < 0) return 0
        parseInto(rest.substring(nameEnd + 1, end), out, spans, depth + 1)
        return end + 1
    }

    private fun parseDelimited(
        rest: String,
        marker: String,
        kind: SpanKind,
        out: StringBuilder,
        spans: MutableList<TextSpan>,
        depth: Int,
    ): Int {
        val close = rest.indexOf(marker, marker.length)
        if (close < 0) return 0
        val inner = rest.substring(marker.length, close)
        if (inner.isEmpty() || '\n' in inner) return 0
        val start = out.length
        parseInto(inner, out, spans, depth + 1)
        spans += TextSpan(start, out.length, kind)
        return close + marker.length
    }

    private fun parseCode(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val close = rest.indexOf('`', 1)
        if (close <= 1) return 0
        val start = out.length
        out.append(rest.substring(1, close))
        spans += TextSpan(start, out.length, SpanKind.CODE)
        return close + 1
    }

    private fun parseMarkdownLink(
        rest: String,
        out: StringBuilder,
        spans: MutableList<TextSpan>,
        depth: Int,
    ): Int {
        val labelEnd = rest.indexOf(']')
        if (labelEnd < 0 || rest.getOrNull(labelEnd + 1) != '(') return 0
        val urlEnd = rest.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return 0
        val url = rest.substring(labelEnd + 2, urlEnd)
        if (!url.startsWith("http")) return 0
        val start = out.length
        parseInto(rest.substring(1, labelEnd), out, spans, depth + 1)
        spans += TextSpan(start, out.length, SpanKind.LINK, url)
        return urlEnd + 1
    }

    private fun parseAngleUrl(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val close = rest.indexOf('>')
        if (close < 0) return 0
        val url = rest.substring(1, close)
        val start = out.length
        out.append(url)
        spans += TextSpan(start, out.length, SpanKind.LINK, url)
        return close + 1
    }

    private fun parseBareUrl(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val raw = rest.takeWhile { !it.isWhitespace() && it != '<' && it != '>' }
        // Trailing punctuation almost always belongs to the sentence, not the URL.
        val url = raw.trimEnd('.', ',', ';', ':', '!', '?', ')')
        if (url.length <= "https://".length) return 0
        val start = out.length
        out.append(url)
        spans += TextSpan(start, out.length, SpanKind.LINK, url)
        return url.length
    }

    private fun parseMention(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val body = rest.drop(1).takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == '@' }
        val acct = body.trimEnd('.', '-')
        if (acct.isEmpty() || acct.count { it == '@' } > 1) return 0
        val text = "@$acct"
        val start = out.length
        out.append(text)
        spans += TextSpan(start, out.length, SpanKind.MENTION, acct)
        return text.length
    }

    private fun parseHashtag(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val body = rest.drop(1).takeWhile { !it.isWhitespace() && it != '#' && it != ':' && it != ',' }
        if (body.isEmpty() || body.all { it.isDigit() }) return 0
        val text = "#$body"
        val start = out.length
        out.append(text)
        spans += TextSpan(start, out.length, SpanKind.HASHTAG, body)
        return text.length
    }

    private fun parseEmoji(rest: String, out: StringBuilder, spans: MutableList<TextSpan>): Int {
        val close = rest.indexOf(':', 1)
        if (close <= 1) return 0
        val shortcode = rest.substring(1, close)
        if (shortcode.isEmpty() || shortcode.any { it.isWhitespace() }) return 0
        if (!shortcode.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '+' || it == '@' }) return 0
        val text = ":$shortcode:"
        val start = out.length
        out.append(text)
        spans += TextSpan(start, out.length, SpanKind.CUSTOM_EMOJI, shortcode)
        return text.length
    }
}
