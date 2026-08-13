package io.github.zalexanninev15.tokitoki.ui.feed

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zalexanninev15.tokitoki.AppContainer
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.ui.components.FeedItemCard
import io.github.zalexanninev15.tokitoki.util.copyToClipboard
import io.github.zalexanninev15.tokitoki.util.openInCustomTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenFollows: (String) -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenProfile: (String, String) -> Unit = { _, _ -> },
    onCompose: (String) -> Unit = {},
    onReply: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.Factory(
            container.feedRepository,
            container.postActionsRepository,
            container.offlineRepository,
        ),
    )
    val followsLabel = stringResource(R.string.action_follows)
    var longPressedTab by remember { mutableStateOf<Int?>(null) }
    val offlineMessage by viewModel.offlineMessage.collectAsStateWithLifecycle()
    val savedTemplate = stringResource(R.string.offline_saved)

    LaunchedEffect(offlineMessage) {
        val message = offlineMessage ?: return@LaunchedEffect
        val text = message.split('/').let { parts ->
            if (parts.size == 2) savedTemplate.format(parts[0], parts[1]) else message
        }
        snackbarHost.showSnackbar(text)
        viewModel.consumeOfflineMessage()
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.tabs.size.coerceAtLeast(1) })

    // The pager is the source of truth for which tab is showing; the view model is told
    // afterwards. Driving it the other way makes the swipe fight the state update.
    LaunchedEffect(pagerState.currentPage) { viewModel.selectTab(pagerState.currentPage) }

    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val copiedMessage = stringResource(R.string.link_copied)

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            // Posts go to the account whose tab is open; on the merged tab there is no
            // unambiguous author, so it falls back to the first connected account.
            val composeAccount = state.tabs.getOrNull(state.selectedTabIndex)?.accountLocalId
                ?: state.tabs.firstOrNull { it.accountLocalId != null }?.accountLocalId
            if (composeAccount != null) {
                FloatingActionButton(onClick = { onCompose(composeAccount) }) {
                    Icon(Icons.Default.Edit, stringResource(R.string.action_new_post))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenAccounts) {
                        Icon(Icons.Default.AccountCircle, stringResource(R.string.accounts))
                    }
                    IconButton(onClick = { viewModel.setSearchVisible(!state.searchVisible) }) {
                        Icon(Icons.Default.Search, stringResource(R.string.search))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Default.Info, stringResource(R.string.about_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            longPressedTab?.let { index ->
                val entry = state.tabs.getOrNull(index)
                DropdownMenu(expanded = true, onDismissRequest = { longPressedTab = null }) {
                    if (entry?.accountLocalId != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_follows)) },
                            onClick = {
                                longPressedTab = null
                                onOpenFollows(entry.accountLocalId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.offline_save_feed)) },
                        onClick = {
                            longPressedTab = null
                            viewModel.saveForOffline(
                                entry?.accountLocalId,
                                state.tabs.mapNotNull { it.accountLocalId },
                            )
                        },
                    )
                }
            }

            if (state.searchVisible) {
                FeedSearchBar(
                    filter = state.filter,
                    onQueryChange = viewModel::updateQuery,
                    onToggleMedia = { viewModel.toggleFilter(media = it) },
                    onToggleUnread = { viewModel.toggleFilter(unread = it) },
                    onToggleReposts = { viewModel.toggleFilter(reposts = it) },
                    onDismiss = { viewModel.setSearchVisible(false) },
                )
            }

            if (state.tabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(
                        0,
                        (state.tabs.size - 1).coerceAtLeast(0),
                    ),
                    edgePadding = 8.dp,
                ) {
                    state.tabs.forEachIndexed { index, entry ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            modifier = Modifier.combinedClickable(
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                onLongClick = { longPressedTab = index },
                                onLongClickLabel = followsLabel,
                            ),
                            text = { Text(entry.title, maxLines = 1) },
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                // Neighbouring pages are pre-composed. With a single shared LazyListState
                // they all drove the same scroll position, which is what made the list
                // stutter; each page gets its own state instead.
                beyondViewportPageCount = 0,
                key = { index -> state.tabs.getOrNull(index)?.accountLocalId ?: "all" },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
            val pageListState = rememberLazyListState()

        // Visibility reporting. layoutInfo already knows how much of each item is on screen,
        // so the fraction is derived from it rather than from per-item position callbacks,
        // which would fire far more often for the same information.
        LaunchedEffect(pageListState, state.items) {
            snapshotFlow { pageListState.layoutInfo }.collect { info ->
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
        LaunchedEffect(pageListState) {
            snapshotFlow {
                val last = pageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last to pageListState.layoutInfo.totalItemsCount
            }.collect { (last, total) ->
                if (total > 0 && last >= total - 5) viewModel.loadMore()
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
                        state = pageListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id.value }) { item ->
                            FeedItemCard(
                                item = item,
                                isRead = item.id.value in state.readIds,
                                // Only in the merged view: on a per-account tab the
                                // source is already the tab you are looking at.
                                sourceLabel = if (state.selectedTabIndex == 0) {
                                    state.sourceLabels[item.id.accountLocalId]
                                } else {
                                    null
                                },
                                onOpenLink = { url -> context.openInCustomTab(url) },
                                onCopyLink = { url ->
                                    if (url != null) {
                                        context.copyToClipboard(url)
                                        scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                                    }
                                },
                                onOpenImage = onOpenImage,
                                onOpenProfile = onOpenProfile,
                                onReply = {
                                    onReply(
                                        item.id.accountLocalId,
                                        item.id.remoteId,
                                        item.author.handle,
                                    )
                                },
                                onBoost = { viewModel.boost(item) },
                                onFavourite = { viewModel.favourite(item) },
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


