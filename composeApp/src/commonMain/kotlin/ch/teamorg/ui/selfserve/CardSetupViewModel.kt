package ch.teamorg.ui.selfserve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.repository.BillingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CardSetupState(
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class CardSetupEvent {
    data object Done : CardSetupEvent()
}

class CardSetupViewModel(
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CardSetupState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<CardSetupEvent>()
    val events = _events.asSharedFlow()

    fun confirm(clubId: String, clientSecret: String) {
        // PaymentSheet's result callback doesn't return the SetupIntent id, but the id is
        // embedded in the client secret we already have, and the backend independently
        // verifies the intent against Stripe anyway, so a client-derived id is safe to trust.
        val setupIntentId = clientSecret.substringBefore("_secret_")

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            billingRepository.confirmBilling(clubId, setupIntentId).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isLoading = false)
                    _events.emit(CardSetupEvent.Done)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to confirm card setup")
                }
            )
        }
    }

    fun onSheetFailed() {
        _state.value = _state.value.copy(error = "Card setup failed. Please try again.")
    }
}
