package io.github.zalexanninev15.tokitoki.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.repo.PostActionsRepository
import io.github.zalexanninev15.tokitoki.domain.model.ComposeTarget
import io.github.zalexanninev15.tokitoki.domain.model.PostVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComposeUiState(
    val text: String = "",
    val contentWarning: String = "",
    val warningVisible: Boolean = false,
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val isSending: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false,
) {
    /** Mastodon's default ceiling; instances may allow more, none allow less. */
    val remaining: Int get() = LIMIT - text.length - contentWarning.length

    val canSend: Boolean
        get() = !isSending && text.isNotBlank() && remaining >= 0

    private companion object {
        const val LIMIT = 500
    }
}

class ComposeViewModel(
    private val repository: PostActionsRepository,
    private val target: ComposeTarget,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    fun updateText(value: String) {
        _state.value = _state.value.copy(text = value, error = null)
    }

    fun updateWarning(value: String) {
        _state.value = _state.value.copy(contentWarning = value)
    }

    fun toggleWarning() {
        val visible = !_state.value.warningVisible
        _state.value = _state.value.copy(
            warningVisible = visible,
            contentWarning = if (visible) _state.value.contentWarning else "",
        )
    }

    fun setVisibility(value: PostVisibility) {
        _state.value = _state.value.copy(visibility = value)
    }

    fun send() {
        val current = _state.value
        if (!current.canSend) return

        viewModelScope.launch {
            _state.value = current.copy(isSending = true, error = null)
            val result = repository.publish(
                target = target,
                text = current.text,
                contentWarning = current.contentWarning.takeIf { it.isNotBlank() },
                visibility = current.visibility,
            )
            _state.value = result.fold(
                onSuccess = { _state.value.copy(isSending = false, sent = true) },
                onFailure = {
                    _state.value.copy(isSending = false, error = it.message ?: "error")
                },
            )
        }
    }

    class Factory(
        private val repository: PostActionsRepository,
        private val target: ComposeTarget,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ComposeViewModel(repository, target) as T
    }
}
