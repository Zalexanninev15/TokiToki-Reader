package io.github.zalexanninev15.tokitoki.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.db.AccountEntity
import io.github.zalexanninev15.tokitoki.data.repo.FeedRepository
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.readsync.MonotonicClock
import io.github.zalexanninev15.tokitoki.domain.readsync.VisibilityReadTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A tab is either the merged view or one specific connected account.
 *
 * Built from the account list rather than being a fixed enum, so adding a second Mastodon
 * account gives it its own tab instead of merging it into a shared one.
 */
data class FeedTab(
    val accountLocalId: String?,
    val title: String,
) {
    val isAll: Boolean get() = accountLocalId == null

    companion object {
        const val ALL_TITLE = "\u0412\u0441\u0435"
    }
}

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val tabs: List<FeedTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    /** Item id -> "misskey · @me@host", shown next to the author handle. */
    val sourceLabels: Map<String, String> = emptyMap(),
    val filter: FeedFilter = FeedFilter(),
    val searchVisible: Boolean = false,
    val readIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasAccounts: Boolean = true,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedViewModel(private val repository: FeedRepository) : ViewModel() {

    private val tabIndex = MutableStateFlow(0)
    private val filter = MutableStateFlow(FeedFilter())
    private val searchVisible = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val seenChannel = Channel<FeedItemId>(Channel.BUFFERED)
    private val tracker = VisibilityReadTracker(clock = MonotonicClock { System.currentTimeMillis() })

    val selectedTabIndex: StateFlow<Int> = tabIndex.asStateFlow()

    private val accounts = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val feed = accounts
        .flatMapLatest { all ->
            val visible = all.filter { it.enabled }
            repository.observeFeed(visible.map { it.localId }).map { items -> all to items }
        }

    // combine, not `map` reading `.value`: a StateFlow read inside map does not make the
    // outer flow re-emit, so switching tabs used to leave the previous list on screen.
    private val flags = combine(refreshing, loadingMore, error, searchVisible) {
            isRefreshing, isLoadingMore, errorMessage, isSearchVisible ->
        Flags(isRefreshing, isLoadingMore, errorMessage, isSearchVisible)
    }

    private data class Flags(
        val refreshing: Boolean,
        val loadingMore: Boolean,
        val error: String?,
        val searchVisible: Boolean,
    )

    val uiState: StateFlow<FeedUiState> = combine(
        feed,
        tabIndex,
        filter,
        flags,
    ) { (all, items), index, currentFilter, flagState ->
        val enabled = all.filter { it.enabled }
        val tabs = buildList {
            add(FeedTab(accountLocalId = null, title = FeedTab.ALL_TITLE))
            enabled.forEach { add(FeedTab(it.localId, it.tabTitle())) }
        }
        val safeIndex = index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val current = tabs.getOrNull(safeIndex)

        val labels = enabled.associate { account -> account.localId to account.sourceLabel() }

        val visible = items.filter {
            current == null || current.isAll || it.id.accountLocalId == current.accountLocalId
        }

        FeedUiState(
            items = currentFilter.apply(visible, emptySet()),
            tabs = tabs,
            selectedTabIndex = safeIndex,
            sourceLabels = labels,
            filter = currentFilter,
            searchVisible = flagState.searchVisible,
            readIds = emptySet(),
            isRefreshing = flagState.refreshing,
            isLoadingMore = flagState.loadingMore,
            hasAccounts = all.isNotEmpty(),
            errorMessage = flagState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        // Acknowledgements are batched: a scroll can produce dozens of "seen" events per
        // second, and each one must not become its own network call.
        viewModelScope.launch {
            seenChannel.consumeAsFlow()
                .debounce(3_000)
                .collect { flushSeen() }
        }
    }

    private val pendingSeen = mutableListOf<FeedItemId>()

    private suspend fun flushSeen() {
        val batch = synchronized(pendingSeen) {
            val copy = pendingSeen.toList()
            pendingSeen.clear()
            copy
        }
        if (batch.isNotEmpty()) repository.markSeen(batch)
    }

    /** Called by the list as items scroll; the dwell rules live in the domain tracker. */
    fun onVisibility(id: FeedItemId, visibleFraction: Float) {
        val seen = tracker.report(id, visibleFraction) ?: return
        synchronized(pendingSeen) { pendingSeen += seen }
        seenChannel.trySend(seen)
    }

    fun onOpened(id: FeedItemId) {
        val seen = tracker.markOpened(id) ?: return
        synchronized(pendingSeen) { pendingSeen += seen }
        seenChannel.trySend(seen)
    }

    /** "Misskey (@user)" — service first, local part of the handle in brackets. */
    private fun AccountEntity.tabTitle(): String = "${prettySource()} (@${localPart()})"

    /** "Misskey:user" — shown under the author handle in the merged feed. */
    private fun AccountEntity.sourceLabel(): String = "${prettySource()}:${localPart()}"

    private fun AccountEntity.prettySource(): String =
        source.lowercase().replaceFirstChar(Char::uppercase)

    private fun AccountEntity.localPart(): String =
        handle.removePrefix("@").substringBefore('@')

    fun updateQuery(value: String) {
        filter.value = filter.value.copy(query = value)
    }

    fun toggleFilter(
        media: Boolean = filter.value.onlyWithMedia,
        unread: Boolean = filter.value.onlyUnread,
        reposts: Boolean = filter.value.hideReposts,
    ) {
        filter.value = filter.value.copy(
            onlyWithMedia = media,
            onlyUnread = unread,
            hideReposts = reposts,
        )
    }

    fun setSearchVisible(visible: Boolean) {
        searchVisible.value = visible
        if (!visible) filter.value = FeedFilter()
    }

    fun selectTab(index: Int) {
        tabIndex.value = index
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            val failures = repository.refresh()
            error.value = failures.firstOrNull()?.message
            tracker.resetDwellTimers()
            refreshing.value = false
        }
    }

    fun loadMore() {
        if (loadingMore.value || refreshing.value) return
        viewModelScope.launch {
            loadingMore.value = true
            val failures = repository.loadMore()
            error.value = failures.firstOrNull()?.message
            loadingMore.value = false
        }
    }

    class Factory(private val repository: FeedRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedViewModel(repository) as T
    }
}
