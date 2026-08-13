package io.github.zalexanninev15.tokitoki.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import java.text.DateFormat
import java.util.Date

@Composable
fun FeedItemCard(
    item: FeedItem,
    isRead: Boolean,
    /** e.g. "mastodon · @me@mastodon.ml" — which connected account this arrived through. */
    sourceLabel: String? = null,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String?) -> Unit,
    onOpenImage: (String) -> Unit,
    /** (accountLocalId, remoteUserId) — enough to load the profile through that account. */
    onOpenProfile: ((String, String) -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onBoost: (() -> Unit)? = null,
    onFavourite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var contentWarningRevealed by remember(item.id.value) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Read items recede rather than disappear: still legible, visibly secondary.
            containerColor = if (isRead) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.repostedBy?.let { booster ->
                Text(
                    text = stringResource(R.string.boosted_by, booster.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = item.author.avatarUrl,
                    contentDescription = stringResource(R.string.cd_avatar, item.author.displayName),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            // Rows cached before the author id was stored have an empty
                            // one; opening those would 404, so the avatar stays inert
                            // until the entry is refreshed.
                            if (onOpenProfile != null && item.author.remoteId.isNotBlank()) {
                                Modifier.clickable {
                                    onOpenProfile(item.id.accountLocalId, item.author.remoteId)
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.author.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = item.author.handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // On its own line below the handle: the two read as one blob when
                    // they sit side by side, since both are @-prefixed.
                    sourceLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = remember(item.createdAtEpochMillis) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(item.createdAtEpochMillis))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val warning = item.contentWarning
            if (warning != null && !contentWarningRevealed) {
                Text(text = warning, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { contentWarningRevealed = true }) {
                    Text(stringResource(R.string.show_content))
                }
            } else {
                if (item.text.plain.isNotBlank()) {
                    RichTextView(text = item.text, onLinkClick = onOpenLink)
                }

                item.media.forEach { media ->
                    val url = media.previewUrl ?: media.url
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = media.description
                                ?: stringResource(R.string.cd_image),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(media.aspectRatio ?: 1.6f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(media.url ?: url) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val canonical = item.canonicalUrl
                TextButton(
                    onClick = { canonical?.let(onOpenLink) },
                    enabled = canonical != null,
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.open_original),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(
                    onClick = { onCopyLink(canonical) },
                    // Disabled rather than silently copying nothing: a private Telegram
                    // channel genuinely has no shareable URL.
                    enabled = canonical != null,
                    modifier = Modifier.semantics {
                        contentDescription = "Copy link to post"
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.copy_link),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            // Only when the source supports it and the screen supplied handlers: the
            // profile view reuses this card in read-only mode.
            val interactions = item.interactions
            if (interactions != null && onReply != null && onBoost != null && onFavourite != null) {
                PostActionsRow(
                    interactions = interactions,
                    onReply = onReply,
                    onBoost = onBoost,
                    onFavourite = onFavourite,
                )
            }
        }
    }
}
