package io.github.zalexanninev15.tokitoki.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.domain.model.PostInteractions

/**
 * Reply / boost / favourite under a post.
 *
 * Counts are shown only when non-zero: a row of zeroes is noise, and on Misskey the
 * reaction total is a sum over many emoji rather than one star, so a "0" there would be
 * actively misleading about what the button does.
 */
@Composable
fun PostActionsRow(
    interactions: PostInteractions,
    onReply: () -> Unit,
    onBoost: () -> Unit,
    onFavourite: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        // Tight against the text: the row used to float away from the post it belongs to.
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = { Icon(Icons.AutoMirrored.Filled.Reply, stringResource(R.string.action_reply), Modifier.size(18.dp)) },
            count = interactions.replyCount,
            highlighted = false,
            onClick = onReply,
        )
        ActionButton(
            icon = { Icon(Icons.Default.Repeat, stringResource(R.string.action_boost), Modifier.size(18.dp)) },
            count = interactions.boostCount,
            highlighted = interactions.boosted,
            onClick = onBoost,
        )
        ActionButton(
            icon = {
                if (interactions.hasReacted) {
                    Icon(Icons.Default.Star, stringResource(R.string.action_favourite), Modifier.size(18.dp))
                } else {
                    Icon(Icons.Outlined.StarOutline, stringResource(R.string.action_favourite), Modifier.size(18.dp))
                }
            },
            count = interactions.favouriteCount,
            highlighted = interactions.hasReacted,
            onClick = onFavourite,
        )

        Spacer(Modifier.weight(1f))

        // Opening and copying were full-width text buttons below the post; as icons they
        // sit on the same line and give the row back to the content.
        onOpen?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.open_original),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        onCopy?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy_link),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        onMore?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    count: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        icon()
        if (count > 0) {
            Text(
                text = " $count",
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
