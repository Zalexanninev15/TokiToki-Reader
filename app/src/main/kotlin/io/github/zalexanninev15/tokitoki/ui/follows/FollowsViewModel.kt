package io.github.zalexanninev15.tokitoki.ui.follows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.repo.FollowsRepository
import io.github.zalexanninev15.tokitoki.domain.model.FollowedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowsUiState(
    val accounts: List<FollowedAccount> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class FollowsViewModel(
    private val repository: FollowsRepository,
    private val accountLocalId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(FollowsUiState())
    val state: StateFlow<FollowsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = FollowsUiState(isLoading = true)
            val result = runCatching { repository.load(accountLocalId) }
            _state.value = result.fold(
                onSuccess = { FollowsUiState(accounts = it, isLoading = false) },
                onFailure = { FollowsUiState(isLoading = false, error = it.message ?: "error") },
            )
        }
    }

    /**
     * Plain JSON so the file is useful outside this app — importing into another client
     * or just keeping a backup of who you follow.
     */
    fun exportJson(): String = buildString {
        append("{\n  \"accounts\": [\n")
        _state.value.accounts.forEachIndexed { index, account ->
            append("    {")
            append("\"handle\": \"").append(account.handle.escapeJson()).append("\", ")
            append("\"name\": \"").append(account.displayName.escapeJson()).append("\", ")
            append("\"url\": \"").append(account.profileUrl.orEmpty().escapeJson()).append("\", ")
            append("\"source\": \"").append(account.source.name.lowercase()).append("\"}")
            if (index != _state.value.accounts.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n}\n")
    }

    private fun String.escapeJson(): String = buildString {
        this@escapeJson.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n', '\r', '\t' -> append(' ')
                else -> append(c)
            }
        }
    }

    class Factory(
        private val repository: FollowsRepository,
        private val accountLocalId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FollowsViewModel(repository, accountLocalId) as T
    }
}
