package io.github.zalexanninev15.tokitoki.data.mastodon.internal

/**
 * Parses RFC 8288 `Link` headers, which is how Mastodon signals pagination.
 *
 * Constructing the next-page URL by hand (appending `?max_id=` to the last item) breaks
 * on instances that sit behind a path prefix or that return fewer items than requested,
 * so the header is the only correct source. Example:
 *
 * ```
 * Link: <https://mastodon.social/api/v1/timelines/home?max_id=109>; rel="next",
 *       <https://mastodon.social/api/v1/timelines/home?min_id=113>; rel="prev"
 * ```
 */
object LinkHeaderParser {

    data class Links(val next: String?, val prev: String?)

    fun parse(header: String?): Links {
        if (header.isNullOrBlank()) return Links(null, null)

        var next: String? = null
        var prev: String? = null

        for (part in splitTopLevel(header)) {
            val urlStart = part.indexOf('<')
            val urlEnd = part.indexOf('>', urlStart + 1)
            if (urlStart < 0 || urlEnd < 0) continue
            val url = part.substring(urlStart + 1, urlEnd).trim()
            if (url.isEmpty()) continue

            when (relOf(part.substring(urlEnd + 1))) {
                "next" -> next = url
                "prev", "previous" -> prev = url
            }
        }
        return Links(next, prev)
    }

    /** Extracts a query parameter, used to turn a next-page URL into an opaque cursor. */
    fun queryParam(url: String, name: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            if (pair.substring(0, eq) == name) return decode(pair.substring(eq + 1))
        }
        return null
    }

    private fun relOf(attributes: String): String? {
        for (attribute in attributes.split(';')) {
            val trimmed = attribute.trim()
            if (!trimmed.startsWith("rel", ignoreCase = true)) continue
            val value = trimmed.substringAfter('=', "").trim().trim('"', '\'')
            if (value.isNotEmpty()) return value.lowercase()
        }
        return null
    }

    /** Splits on commas that are not inside angle brackets or quotes. */
    private fun splitTopLevel(header: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inAngle = false
        var inQuote = false

        for (c in header) {
            when {
                c == '"' -> { inQuote = !inQuote; current.append(c) }
                c == '<' && !inQuote -> { inAngle = true; current.append(c) }
                c == '>' && !inQuote -> { inAngle = false; current.append(c) }
                c == ',' && !inAngle && !inQuote -> {
                    if (current.isNotBlank()) parts += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts
    }

    private fun decode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> { out.append(' '); i++ }
                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) { out.append(hex.toChar()); i += 3 } else { out.append(c); i++ }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
