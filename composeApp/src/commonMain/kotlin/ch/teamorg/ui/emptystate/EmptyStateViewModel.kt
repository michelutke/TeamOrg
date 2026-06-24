package ch.teamorg.ui.emptystate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmptyStateUiState(
    val inviteLink: String = "",
    val profileLink: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val isSuperAdmin: Boolean = false
)

sealed class EmptyStateEvent {
    data object NavigateToClubSetup : EmptyStateEvent()
    data class NavigateToInvite(val token: String) : EmptyStateEvent()
}

class EmptyStateViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmptyStateUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<EmptyStateEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProfileLink()
    }

    private fun loadProfileLink() {
        viewModelScope.launch {
            authRepository.getMe().onSuccess { user ->
                _state.value = _state.value.copy(
                    profileLink = "teamorg://invite/player/${user.userId}",
                    isSuperAdmin = user.isSuperAdmin
                )
            }
        }
    }

    fun onInviteLinkChange(link: String) {
        _state.value = _state.value.copy(inviteLink = link, error = null)
    }

    fun onJoinTeamClick() {
        val link = _state.value.inviteLink
        if (link.isBlank()) {
            _state.value = _state.value.copy(error = "Please paste an invite link")
            return
        }

        // Extract token from supported link formats, else treat the whole input as the token.
        val trimmed = link.trim()
        val token = when {
            trimmed.contains("/i/") -> trimmed.substringAfterLast("/i/")
            trimmed.startsWith("teamorg://invite/team/") -> trimmed.substringAfterLast("/")
            else -> trimmed
        }

        if (token.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid invite link")
            return
        }

        viewModelScope.launch {
            _events.emit(EmptyStateEvent.NavigateToInvite(token))
        }
    }

    fun onCreateClubClick() {
        viewModelScope.launch {
            _events.emit(EmptyStateEvent.NavigateToClubSetup)
        }
    }

    fun onProfileLinkCopied() {
        _state.value = _state.value.copy(infoMessage = "Link copied to clipboard")
    }

    fun dismissMessages() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }
}
