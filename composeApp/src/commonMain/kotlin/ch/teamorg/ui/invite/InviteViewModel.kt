package ch.teamorg.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.InviteDetails
import ch.teamorg.domain.RedeemResult
import ch.teamorg.repository.AuthRepository
import ch.teamorg.repository.InviteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InviteState(
    val inviteDetails: InviteDetails? = null,
    val isLoading: Boolean = false,
    val isRedeeming: Boolean = false,
    val error: String? = null,
    val isRedeemed: Boolean = false,
    val showLogout: Boolean = false
)

class InviteViewModel(
    private val inviteRepository: InviteRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InviteState())
    val state = _state.asStateFlow()

    fun loadInvite(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            inviteRepository.getInviteDetails(token).fold(
                onSuccess = { details ->
                    _state.value = _state.value.copy(
                        inviteDetails = details,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to fetch invite details"
                    )
                }
            )
        }
    }

    fun redeemInvite(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRedeeming = true, error = null, showLogout = false)
            when (val result = inviteRepository.redeemInvite(token)) {
                is RedeemResult.Success -> {
                    _state.value = _state.value.copy(isRedeeming = false, isRedeemed = true)
                }
                is RedeemResult.EmailMismatch -> {
                    val currentEmail = authRepository.getMe().getOrNull()?.email
                    val message = buildString {
                        append("Diese Einladung ist für ")
                        append(result.invitedEmail ?: "eine andere E-Mail-Adresse")
                        append(".")
                        if (currentEmail != null) {
                            append(" Du bist als ")
                            append(currentEmail)
                            append(" angemeldet.")
                        }
                    }
                    _state.value = _state.value.copy(
                        isRedeeming = false,
                        error = message,
                        showLogout = true
                    )
                }
                is RedeemResult.Expired -> {
                    _state.value = _state.value.copy(
                        isRedeeming = false,
                        error = "Diese Einladung ist abgelaufen."
                    )
                }
                is RedeemResult.Inactive -> {
                    _state.value = _state.value.copy(
                        isRedeeming = false,
                        error = "Diese Einladung ist nicht mehr gültig."
                    )
                }
                is RedeemResult.NotFound -> {
                    _state.value = _state.value.copy(
                        isRedeeming = false,
                        error = "Diese Einladung wurde nicht gefunden."
                    )
                }
                is RedeemResult.Error -> {
                    _state.value = _state.value.copy(
                        isRedeeming = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
