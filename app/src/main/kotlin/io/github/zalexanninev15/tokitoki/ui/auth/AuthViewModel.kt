package io.github.zalexanninev15.tokitoki.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.repo.AuthService
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val source: SourceKind = SourceKind.MASTODON,
    val instanceUrl: String = "",
    val busy: Boolean = false,
    val authorizeUrl: String? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val authService: AuthService) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun selectSource(source: SourceKind) = _state.update { it.copy(source = source, error = null) }

    fun updateInstanceUrl(value: String) = _state.update { it.copy(instanceUrl = value, error = null) }

    fun beginSignIn() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                when (current.source) {
                    SourceKind.MASTODON -> authService.beginMastodon(current.instanceUrl)
                    SourceKind.MISSKEY -> authService.beginMisskey(current.instanceUrl)
                    SourceKind.TELEGRAM -> error("Telegram is not supported yet")
                }
            }.onSuccess { url ->
                _state.update { it.copy(busy = false, authorizeUrl = url) }
            }.onFailure { failure ->
                _state.update { it.copy(busy = false, error = failure.message ?: "unknown error") }
            }
        }
    }

    fun onAuthorizeUrlConsumed() = _state.update { it.copy(authorizeUrl = null) }

    fun completeSignIn(callback: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            authService.complete(callback)
                .onSuccess { _state.update { s -> s.copy(busy = false, completed = true) } }
                .onFailure { failure ->
                    _state.update { s ->
                        s.copy(busy = false, error = failure.message ?: "sign-in failed")
                    }
                }
        }
    }

    class Factory(private val authService: AuthService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(authService) as T
    }
}
