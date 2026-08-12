package io.github.zalexanninev15.tokitoki.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind

private const val TAG_LINK = "link"

/**
 * Renders the source-agnostic [RichText].
 *
 * Because Mastodon HTML, Misskey MFM and Telegram entities are all normalised upstream,
 * there is exactly one renderer here rather than one per service.
 */
@Composable
fun RichTextView(
    text: RichText,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val annotated = remember(text, colors) { text.toAnnotatedString(colors.primary, colors.tertiary) }
    val style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)

    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(TAG_LINK, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        },
    )
}

private fun RichText.toAnnotatedString(
    linkColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    append(plain)

    spans.forEach { span ->
        // Spans were validated against the text at parse time, but a corrupted cache row
        // could still be out of range; clamping is cheaper than crashing the feed.
        val start = span.start.coerceIn(0, plain.length)
        val end = span.end.coerceIn(start, plain.length)
        if (start == end) return@forEach

        val style = when (span.kind) {
            SpanKind.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            SpanKind.MENTION, SpanKind.HASHTAG -> SpanStyle(color = linkColor)
            SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanKind.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
            SpanKind.QUOTE -> SpanStyle(color = accentColor)
            SpanKind.CUSTOM_EMOJI -> SpanStyle(color = accentColor)
        }
        addStyle(style, start, end)

        span.target?.let { target ->
            if (span.kind == SpanKind.LINK) {
                addStringAnnotation(TAG_LINK, target, start, end)
            }
        }
    }
}
