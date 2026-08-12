package io.github.zalexanninev15.tokitoki.data.mastodon.internal

import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind
import io.github.zalexanninev15.tokitoki.domain.model.TextSpan

/**
 * Converts Mastodon's sanitised status HTML into the app-wide [RichText].
 *
 * Deliberately hand-written rather than delegating to `Html.fromHtml`: the platform
 * parser drops the anchor classes that distinguish a mention from a hashtag from an
 * ordinary link, and it renders the `invisible` spans that Mastodon uses to shorten long
 * URLs, producing text like "https://example.com/very/long/pathexample.com/very/lo…".
 *
 * Mastodon's own sanitiser restricts status HTML to a small tag whitelist, so a full
 * HTML5 parser would be overkill. Unknown tags are skipped, their contents kept.
 */
object MastodonHtmlParser {

    private val BLOCK_TAGS = setOf("p", "div", "blockquote", "pre", "ul", "ol")

    fun parse(html: String?): RichText {
        if (html.isNullOrEmpty()) return RichText.EMPTY

        val out = StringBuilder()
        val spans = mutableListOf<TextSpan>()
        val open = ArrayDeque<OpenTag>()
        var invisibleDepth = 0
        var i = 0

        while (i < html.length) {
            val c = html[i]
            if (c != '<') {
                val end = html.indexOf('<', i).let { if (it < 0) html.length else it }
                if (invisibleDepth == 0) out.append(decodeEntities(html.substring(i, end)))
                i = end
                continue
            }

            val tagEnd = html.indexOf('>', i)
            if (tagEnd < 0) {
                if (invisibleDepth == 0) out.append(decodeEntities(html.substring(i)))
                break
            }

            val raw = html.substring(i + 1, tagEnd).trim()
            i = tagEnd + 1

            val closing = raw.startsWith("/")
            val body = raw.removePrefix("/").removeSuffix("/").trim()
            val name = body.takeWhile { !it.isWhitespace() }.lowercase()

            when {
                name == "br" -> if (invisibleDepth == 0) out.append('\n')

                closing -> {
                    if (name == "span" && invisibleDepth > 0) {
                        invisibleDepth--
                        continue
                    }
                    val opened = open.lastOrNull { it.name == name }
                    if (opened != null) {
                        open.remove(opened)
                        opened.kind?.let { kind ->
                            if (out.length > opened.start) {
                                spans += TextSpan(opened.start, out.length, kind, opened.target)
                            }
                        }
                    }
                    if (name in BLOCK_TAGS) appendBlockBreak(out)
                }

                else -> {
                    val attributes = body.drop(name.length)
                    if (name == "span") {
                        // Mastodon marks the hidden halves of a shortened URL as invisible.
                        if ("invisible" in classOf(attributes)) invisibleDepth++
                        continue
                    }
                    if (invisibleDepth > 0) continue

                    val kind = when (name) {
                        "a" -> anchorKind(attributes)
                        "strong", "b" -> SpanKind.BOLD
                        "em", "i" -> SpanKind.ITALIC
                        "del", "s" -> SpanKind.STRIKETHROUGH
                        "code" -> SpanKind.CODE
                        "blockquote" -> SpanKind.QUOTE
                        else -> null
                    }
                    val target = if (name == "a") attributeOf(attributes, "href") else null
                    if (kind != null || name in BLOCK_TAGS) {
                        open.addLast(OpenTag(name, out.length, kind, target))
                    }
                }
            }
        }

        // Anything still open at EOF (malformed markup) closes at the end of the text.
        for (tag in open) {
            tag.kind?.let { kind ->
                if (out.length > tag.start) spans += TextSpan(tag.start, out.length, kind, tag.target)
            }
        }

        val plain = out.toString().trim('\n', ' ')
        val trimmedFront = out.length - out.toString().trimStart('\n', ' ').length
        val adjusted = spans
            .map { TextSpan(it.start - trimmedFront, it.end - trimmedFront, it.kind, it.target) }
            .filter { it.start >= 0 && it.end <= plain.length && it.length > 0 }
            .sortedBy { it.start }

        return RichText(plain, adjusted)
    }

    private data class OpenTag(
        val name: String,
        val start: Int,
        val kind: SpanKind?,
        val target: String?,
    )

    private fun appendBlockBreak(out: StringBuilder) {
        if (out.isEmpty()) return
        if (out.endsWith("\n\n")) return
        out.append(if (out.endsWith("\n")) "\n" else "\n\n")
    }

    private fun anchorKind(attributes: String): SpanKind {
        val classes = classOf(attributes)
        return when {
            "hashtag" in classes -> SpanKind.HASHTAG
            "mention" in classes -> SpanKind.MENTION
            else -> SpanKind.LINK
        }
    }

    private fun classOf(attributes: String): Set<String> =
        attributeOf(attributes, "class")?.split(' ')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    internal fun attributeOf(attributes: String, name: String): String? {
        var index = 0
        while (true) {
            index = attributes.indexOf(name, index, ignoreCase = true)
            if (index < 0) return null
            val before = attributes.getOrNull(index - 1)
            val afterName = attributes.drop(index + name.length).trimStart()
            index += name.length
            if (before != null && !before.isWhitespace()) continue
            if (!afterName.startsWith("=")) continue
            val value = afterName.drop(1).trimStart()
            return when {
                value.startsWith('"') -> value.drop(1).substringBefore('"')
                value.startsWith('\'') -> value.drop(1).substringBefore('\'')
                else -> value.takeWhile { !it.isWhitespace() && it != '>' }
            }.let(::decodeEntities)
        }
    }

    internal fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] != '&') { out.append(text[i]); i++; continue }
            val semi = text.indexOf(';', i + 1)
            if (semi < 0 || semi - i > 10) { out.append(text[i]); i++; continue }
            val entity = text.substring(i + 1, semi)
            val replacement = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" || entity == "#39" -> "'"
                entity == "nbsp" -> "\u00A0"
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                entity.startsWith("#") ->
                    entity.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> null
            }
            if (replacement != null) { out.append(replacement); i = semi + 1 }
            else { out.append(text[i]); i++ }
        }
        return out.toString()
    }
}
