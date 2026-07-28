package ch.teamorg.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.data.repository.BillingInfo
import ch.teamorg.payments.setupIntentIdFrom
import ch.teamorg.repository.BillingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BillingUiState(
    val isLoading: Boolean = false,
    val billingInfo: BillingInfo? = null,
    val notOwner: Boolean = false,
    val error: String? = null
)

sealed class BillingEvent {
    data class PresentCardSheet(val publishableKey: String, val clientSecret: String) : BillingEvent()
}

class BillingViewModel(
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BillingUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>()
    val events = _events.asSharedFlow()

    private var clubId: String? = null

    fun load(clubId: String) {
        this.clubId = clubId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notOwner = false)
            billingRepository.getBilling(clubId).fold(
                onSuccess = { billingInfo ->
                    _state.value = _state.value.copy(isLoading = false, billingInfo = billingInfo, notOwner = false)
                },
                onFailure = { e ->
                    if (e.message?.contains(": 403") == true) {
                        _state.value = _state.value.copy(isLoading = false, notOwner = true)
                    } else {
                        _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load billing")
                    }
                }
            )
        }
    }

    fun updateCard() {
        val clubId = clubId ?: return
        viewModelScope.launch {
            billingRepository.startCardUpdate(clubId).fold(
                onSuccess = { cardUpdateStart ->
                    _events.emit(
                        BillingEvent.PresentCardSheet(
                            publishableKey = cardUpdateStart.publishableKey,
                            clientSecret = cardUpdateStart.setupIntentClientSecret
                        )
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to start card update")
                }
            )
        }
    }

    fun onCardSetupCompleted(clientSecret: String) {
        val clubId = clubId ?: return
        val setupIntentId = setupIntentIdFrom(clientSecret)
        viewModelScope.launch {
            billingRepository.confirmBilling(clubId, setupIntentId).fold(
                onSuccess = { load(clubId) },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to confirm card setup")
                }
            )
        }
    }

    fun onCardSetupFailed() {
        _state.value = _state.value.copy(error = "Card setup failed. Please try again.")
    }

    fun convert() {
        val clubId = clubId ?: return
        val currentKind = _state.value.billingInfo?.kind ?: return
        val target = if (currentKind == "club") "team" else "club"
        viewModelScope.launch {
            billingRepository.convert(clubId, target).fold(
                onSuccess = { load(clubId) },
                onFailure = { e ->
                    if (e.message?.contains(": 409") == true) {
                        _state.value = _state.value.copy(error = "Only possible with exactly one active team.")
                    } else {
                        _state.value = _state.value.copy(error = e.message ?: "Failed to convert")
                    }
                }
            )
        }
    }
}
