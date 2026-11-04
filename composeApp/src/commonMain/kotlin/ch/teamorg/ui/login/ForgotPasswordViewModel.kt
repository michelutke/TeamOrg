package ch.teamorg.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val confirmationShown: Boolean = false
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state = _state.asStateFlow()

    fun onEmailChange(email: String) {
        _state.value = _state.value.copy(email = email, error = null)
    }

    fun onSubmitClick() {
        val currentState = _state.value
        if (currentState.isLoading) return

        val email = currentState.email.trim()
        if (!isValidEmail(email)) {
            _state.value = currentState.copy(error = "Please enter a valid email address")
            return
        }

        _state.value = currentState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // The outcome is deliberately ignored: the confirmation is identical either way so
            // the screen never reveals whether an account exists for the address.
            authRepository.forgotPassword(email)
            _state.value = _state.value.copy(isLoading = false, confirmationShown = true)
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.length <= 254 && email.contains("@") && email.indexOf("@") < email.lastIndexOf(".")
}
