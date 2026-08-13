package io.github.zalexanninev15.tokitoki.ui.follows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.domain.model.FollowedAccount
import io.github.zalexanninev15.tokitoki.util.Downloads
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowsScreen(
    viewModel: FollowsViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val exportedMessage = stringResource(R.string.follows_exported)
    val exportFailedMessage = stringResource(R.string.follows_export_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.follows_title))
                        if (state.accounts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.follows_count, state.accounts.size),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.accounts.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val result = Downloads.saveText(
                                    context,
                                    "tokitoki_follows_${System.currentTimeMillis()}.json",
                                    viewModel.exportJson(),
                                )
                                snackbar.showSnackbar(
                                    result.fold(
                                        onSuccess = { name -> exportedMessage.format(name) },
                                        onFailure = { exportFailedMessage },
                                    ),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Default.Download, stringResource(R.string.action_export))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Text(
                    text = state.error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )

                state.accounts.isEmpty() -> Text(
                    text = stringResource(R.string.follows_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.accounts, key = { "${it.source}:${it.remoteId}" }) { account ->
                        FollowRow(account, onClick = { account.profileUrl?.let(onOpenProfile) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRow(account: FollowedAccount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 48dp minimum touch target, plus padding, keeps this reachable.
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = account.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (account.isBot) {
            Text(
                text = stringResource(R.string.follows_bot),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
