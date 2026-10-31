package ch.teamorg.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    sessionExpiredNotifier: SessionExpiredNotifier = SessionExpiredNotifier()
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionExpiredNotifier.events.collect { _state.value = AuthState.Unauthenticated }
        }
        checkAuthState()
    }

    fun checkAuthState() {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            if (authRepository.isLoggedIn()) {
                authRepository.getMe().fold(
                    onSuccess = { user ->
                        val hasTeam = authRepository.hasTeam()
                        if (!authRepository.isLoggedIn()) {
                            _state.value = AuthState.Unauthenticated
                            return@fold
                        }
                        _state.value = AuthState.Authenticated(user, hasTeam = hasTeam)
                    },
                    onFailure = {
                        _state.value = AuthState.Unauthenticated
                    }
                )
            } else {
                _state.value = AuthState.Unauthenticated
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _state.value = AuthState.Unauthenticated
    }
}
