package io.github.zalexanninev15.tokitoki.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.repo.FeedRepository
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.readsync.MonotonicClock
import io.github.zalexanninev15.tokitoki.domain.readsync.VisibilityReadTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTab { ALL, MASTODON, MISSKEY }

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasAccounts: Boolean = true,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedViewModel(private val repository: FeedRepository) : ViewModel() {

    private val tab = MutableStateFlow(FeedTab.ALL)
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val seenChannel = Channel<FeedItemId>(Channel.BUFFERED)
    private val tracker = VisibilityReadTracker(clock = MonotonicClock { System.currentTimeMillis() })

    val selectedTab: StateFlow<FeedTab> = tab.asStateFlow()

    private val accounts = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<FeedUiState> = accounts
        .flatMapLatest { all ->
            val visible = all.filter { it.enabled }
            repository.observeFeed(visible.map { it.localId }).map { items -> all to items }
        }
        .map { (all, items) ->
            FeedUiState(
                items = items.filter { it.matches(tab.value) },
                readIds = emptySet(),
                isRefreshing = refreshing.value,
                isLoadingMore = loadingMore.value,
                hasAccounts = all.isNotEmpty(),
                errorMessage = error.value,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

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

    fun selectTab(next: FeedTab) {
        tab.value = next
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

    private fun FeedItem.matches(current: FeedTab): Boolean = when (current) {
        FeedTab.ALL -> true
        FeedTab.MASTODON -> id.source == SourceKind.MASTODON
        FeedTab.MISSKEY -> id.source == SourceKind.MISSKEY
    }

    class Factory(private val repository: FeedRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedViewModel(repository) as T
    }
}
