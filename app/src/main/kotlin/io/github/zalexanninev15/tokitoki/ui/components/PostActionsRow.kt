package io.github.zalexanninev15.tokitoki.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
