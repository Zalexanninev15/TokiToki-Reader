package io.github.zalexanninev15.tokitoki.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind

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
    val annotated = remember(text, colors, onLinkClick) {
        text.toAnnotatedString(colors.primary, colors.tertiary, onLinkClick)
    }
    val style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)

    // SelectionContainer plus LinkAnnotation rather than ClickableText: the old widget
    // swallows the long-press that starts a selection, so links and copying could not
    // both work. LinkAnnotation keeps taps on links while the rest stays selectable.
    SelectionContainer(modifier = modifier) {
        Text(text = annotated, style = style)
    }
}

private fun RichText.toAnnotatedString(
    linkColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
    onLinkClick: (String) -> Unit,
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
                addLink(
                    LinkAnnotation.Url(
                        url = target,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { onLinkClick(target) },
                    ),
                    start,
                    end,
                )
            }
        }
    }
}
