package io.github.zalexanninev15.tokitoki.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zalexanninev15.tokitoki.data.repo.Profile
import io.github.zalexanninev15.tokitoki.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val accountLocalId: String,
    private val remoteUserId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ProfileUiState(isLoading = true)
            val result = runCatching { repository.load(accountLocalId, remoteUserId) }
            _state.value = result.fold(
                onSuccess = { ProfileUiState(profile = it, isLoading = false) },
                onFailure = { ProfileUiState(isLoading = false, error = it.message ?: "error") },
            )
        }
    }

    class Factory(
        private val repository: ProfileRepository,
        private val accountLocalId: String,
        private val remoteUserId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repository, accountLocalId, remoteUserId) as T
    }
}
