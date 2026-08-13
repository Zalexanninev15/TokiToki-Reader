package io.github.zalexanninev15.tokitoki.ui.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.browser.customtabs.CustomTabsIntent
import io.github.zalexanninev15.tokitoki.AppContainer
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.ui.components.FeedItemCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenFollows: (String) -> Unit = {},
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.Factory(container.feedRepository),
    )
    val accounts by container.feedRepository.observeAccounts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val followsLabel = stringResource(R.string.action_follows)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val copiedMessage = stringResource(R.string.link_copied)

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Visibility reporting. layoutInfo already knows how much of each item is on screen,
    // so the fraction is derived from it rather than from per-item position callbacks,
    // which would fire far more often for the same information.
    LaunchedEffect(listState, state.items) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            val viewportStart = info.viewportStartOffset
            val viewportEnd = info.viewportEndOffset
            info.visibleItemsInfo.forEach { visible ->
                val item = state.items.getOrNull(visible.index) ?: return@forEach
                if (visible.size <= 0) return@forEach
                val top = visible.offset.coerceAtLeast(viewportStart)
                val bottom = (visible.offset + visible.size).coerceAtMost(viewportEnd)
                val fraction = ((bottom - top).toFloat() / visible.size).coerceIn(0f, 1f)
                viewModel.onVisibility(item.id, fraction)
            }
        }
    }

    // Infinite scroll: request the next page while a screenful still remains.
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to listState.layoutInfo.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) viewModel.loadMore()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenAccounts) {
                        Icon(Icons.Default.AccountCircle, stringResource(R.string.accounts))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                FeedTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { viewModel.selectTab(entry) },
                        modifier = Modifier.combinedClickable(
                            onClick = { viewModel.selectTab(entry) },
                            onLongClick = {
                                // Long press jumps to the subscriptions of the first
                                // connected account for that source.
                                val source = when (entry) {
                                    FeedTab.MASTODON -> SourceKind.MASTODON
                                    FeedTab.MISSKEY -> SourceKind.MISSKEY
                                    FeedTab.ALL -> null
                                }
                                val target = accounts.firstOrNull {
                                    source == null || it.source == source.name
                                }
                                target?.let { onOpenFollows(it.localId) }
                            },
                            onLongClickLabel = followsLabel,
                        ),
                        text = {
                            Text(
                                when (entry) {
                                    FeedTab.ALL -> stringResource(R.string.tab_all)
                                    FeedTab.MASTODON -> stringResource(R.string.tab_mastodon)
                                    FeedTab.MISSKEY -> stringResource(R.string.tab_misskey)
                                },
                            )
                        },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.items.isEmpty() && !state.isRefreshing -> EmptyOrError(
                        hasAccounts = state.hasAccounts,
                        error = state.errorMessage,
                        onRetry = viewModel::refresh,
                        onAddAccount = onOpenAccounts,
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id.value }) { item ->
                            FeedItemCard(
                                item = item,
                                isRead = item.id.value in state.readIds,
                                onOpenLink = { url -> context.openInCustomTab(url) },
                                onCopyLink = { url ->
                                    if (url != null) {
                                        context.copyToClipboard(url)
                                        scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                                    }
                                },
                                onOpenImage = onOpenImage,
                            )
                        }

                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOrError(
    hasAccounts: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onAddAccount: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(
                    if (error != null) R.string.feed_error_title else R.string.feed_empty_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = error ?: stringResource(R.string.feed_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasAccounts) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            } else {
                TextButton(onClick = onAddAccount) { Text(stringResource(R.string.add_account)) }
            }
        }
    }
}

private fun Context.openInCustomTab(url: String) {
    runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, url.toUri())
    }
}

private fun Context.copyToClipboard(url: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("post", url))
}
