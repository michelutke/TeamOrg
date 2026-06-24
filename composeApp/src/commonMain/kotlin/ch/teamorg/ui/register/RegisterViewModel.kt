package ch.teamorg.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.DeepLinkHandler
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val emailLocked: Boolean = false
)

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        DeepLinkHandler.pendingInviteEmail.value.let { lockedEmail ->
            RegisterUiState(
                email = lockedEmail ?: "",
                emailLocked = lockedEmail != null
            )
        }
    )
    val state = _state.asStateFlow()

    private val _registerSuccess = Channel<Unit>(Channel.CONFLATED)
    val registerSuccess = _registerSuccess.receiveAsFlow()

    fun onDisplayNameChange(name: String) {
        _state.value = _state.value.copy(displayName = name, error = null)
    }

    fun onEmailChange(email: String) {
        if (_state.value.emailLocked) return
        _state.value = _state.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _state.value = _state.value.copy(confirmPassword = password, error = null)
    }

    fun onRegisterClick() {
        val currentState = _state.value
        if (currentState.isLoading) return

        if (currentState.displayName.isBlank() || currentState.email.isBlank() ||
            currentState.password.isBlank() || currentState.confirmPassword.isBlank()) {
            _state.value = currentState.copy(error = "Please fill in all fields")
            return
        }

        if (currentState.password.length < 8) {
            _state.value = currentState.copy(error = "Password must be at least 8 characters")
            return
        }

        if (currentState.password != currentState.confirmPassword) {
            _state.value = currentState.copy(error = "Passwords do not match")
            return
        }

        _state.value = currentState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            authRepository.register(
                RegisterRequest(
                    email = currentState.email,
                    password = currentState.password,
                    displayName = currentState.displayName
                )
            ).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isLoading = false)
                    DeepLinkHandler.pendingInviteEmail.value = null
                    _registerSuccess.send(Unit)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Registration failed"
                    )
                }
            )
        }
    }
}
