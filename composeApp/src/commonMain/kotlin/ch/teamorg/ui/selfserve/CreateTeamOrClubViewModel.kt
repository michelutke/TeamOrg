package ch.teamorg.ui.selfserve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.data.repository.SelfServeCreated
import ch.teamorg.repository.AuthRepository
import ch.teamorg.repository.BillingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateTeamOrClubUiState(
    val kind: String = "team",
    val name: String = "",
    val sportType: String = "volleyball",
    val location: String = "",
    val billingEmail: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class CreateTeamOrClubEvent {
    data class ProceedToCard(val created: SelfServeCreated) : CreateTeamOrClubEvent()
}

class CreateTeamOrClubViewModel(
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTeamOrClubUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateTeamOrClubEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.getMe().onSuccess { user ->
                _state.value = _state.value.copy(billingEmail = user.email)
            }
        }
    }

    fun onKindChange(kind: String) {
        _state.value = _state.value.copy(kind = kind, error = null)
    }

    fun onNameChange(name: String) {
        _state.value = _state.value.copy(name = name, error = null)
    }

    fun onSportTypeChange(sportType: String) {
        _state.value = _state.value.copy(sportType = sportType, error = null)
    }

    fun onLocationChange(location: String) {
        _state.value = _state.value.copy(location = location, error = null)
    }

    fun onBillingEmailChange(billingEmail: String) {
        _state.value = _state.value.copy(billingEmail = billingEmail, error = null)
    }

    fun submit() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.value = current.copy(error = "Name must not be empty")
            return
        }
        if (!current.billingEmail.contains("@")) {
            _state.value = current.copy(error = "Invalid email address")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            billingRepository.createSelfServe(
                kind = current.kind,
                name = current.name,
                sportType = current.sportType.ifBlank { null },
                location = current.location.ifBlank { null },
                billingEmail = current.billingEmail
            ).fold(
                onSuccess = { created ->
                    _state.value = _state.value.copy(isLoading = false)
                    _events.emit(CreateTeamOrClubEvent.ProceedToCard(created))
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Creation failed")
                }
            )
        }
    }
}
