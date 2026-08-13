package io.github.zalexanninev15.tokitoki.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zalexanninev15.tokitoki.R

/**
 * Search and filter strip shown above the feed.
 *
 * Filters the cached timeline rather than querying the servers — see [FeedFilter] for
 * why. The chips stay on one scrollable row so the strip never pushes the feed down by
 * more than a single line on a narrow screen.
 */
@Composable
fun FeedSearchBar(
    filter: FeedFilter,
    onQueryChange: (String) -> Unit,
    onToggleMedia: (Boolean) -> Unit,
    onToggleUnread: (Boolean) -> Unit,
    onToggleReposts: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.cd_close))
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.onlyWithMedia,
                onClick = { onToggleMedia(!filter.onlyWithMedia) },
                label = { Text(stringResource(R.string.filter_media)) },
            )
            FilterChip(
                selected = filter.onlyUnread,
                onClick = { onToggleUnread(!filter.onlyUnread) },
                label = { Text(stringResource(R.string.filter_unread)) },
            )
            FilterChip(
                selected = filter.hideReposts,
                onClick = { onToggleReposts(!filter.hideReposts) },
                label = { Text(stringResource(R.string.filter_no_reposts)) },
            )
        }
    }
}
