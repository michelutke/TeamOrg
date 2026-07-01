package ch.teamorg.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.SubmitResponseRequest
import ch.teamorg.repository.AttendanceRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.NotificationRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class FinalizeBlockedState(
    val reason: String,
    val memberNames: List<String>   // display names (id fallback) for the dialog
)

data class EventDetailState(
    val event: EventWithTeams? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCoach: Boolean = false,
    val myResponse: String? = null,
    val confirmedCount: Int = 0,
    val maybeCount: Int = 0,
    val declinedCount: Int = 0,
    val responseDeadline: Instant? = null,
    val deadlinePassed: Boolean = false,
    val attendanceResponses: List<AttendanceResponse> = emptyList(),
    val reminderLeadMinutes: Int? = null,
    val isLoadingReminder: Boolean = false,
    val isFinalizingOrReopening: Boolean = false,
    val finalizeBlocked: FinalizeBlockedState? = null
)

sealed class DetailEvent {
    data object Cancelled : DetailEvent()
    data object Uncancelled : DetailEvent()
}

class EventDetailViewModel(
    private val eventRepository: EventRepository,
    private val teamRepository: TeamRepository,
    private val attendanceRepository: AttendanceRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EventDetailState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>()
    val events = _events.asSharedFlow()

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            eventRepository.getEventDetail(eventId)
                .onSuccess { ewt ->
                    _state.update { it.copy(event = ewt, isLoading = false) }
                    checkCoachRole()
                    loadAttendance(eventId)
                    loadReminderOverride(eventId)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun loadAttendance(eventId: String) {
        viewModelScope.launch {
            attendanceRepository.getMyResponse(eventId).onSuccess { response ->
                _state.update { it.copy(myResponse = response?.status) }
            }

            attendanceRepository.getEventAttendance(eventId).onSuccess { responses ->
                val confirmed = responses.count { it.status == "confirmed" }
                val maybe = responses.count { it.status == "unsure" }
                val declined = responses.count {
                    it.status == "declined" || it.status == "declined-auto"
                }
                _state.update {
                    it.copy(
                        confirmedCount = confirmed,
                        maybeCount = maybe,
                        declinedCount = declined,
                        attendanceResponses = responses
                    )
                }
            }
        }
    }

    fun submitResponse(status: String, reason: String?) {
        val eventId = _state.value.event?.event?.id ?: return
        _state.update { it.copy(myResponse = status) }
        viewModelScope.launch {
            val request = SubmitResponseRequest(status = status, reason = reason)
            attendanceRepository.submitResponse(eventId, request)
                .onSuccess { response ->
                    _state.update { it.copy(myResponse = response.status) }
                    loadAttendance(eventId)
                }
                .onFailure {
                    _state.update { it.copy(myResponse = null) }
                }
        }
    }

    fun setMemberResponse(userId: String, status: String, unexcused: Boolean) {
        val eventId = _state.value.event?.event?.id ?: return
        viewModelScope.launch {
            attendanceRepository.setMemberResponse(eventId, userId, status, unexcused)
                .onSuccess { loadAttendance(eventId) }
        }
    }

    fun finalize() {
        val eventId = _state.value.event?.event?.id ?: return
        _state.update { it.copy(isFinalizingOrReopening = true) }
        viewModelScope.launch {
            when (val result = attendanceRepository.finalize(eventId)) {
                is FinalizeResult.Success -> {
                    _state.update { it.copy(isFinalizingOrReopening = false) }
                    loadEvent(eventId)
                }
                is FinalizeResult.Blocked -> {
                    // AttendanceResponse has no display name; use userId as fallback label
                    val memberNames = result.userIds
                    val blockedMessage = when (result.reason) {
                        "unsure" -> "Unsichere Spieler müssen zuerst als anwesend oder abwesend markiert werden."
                        "no-response" -> "Spieler ohne Antwort müssen zuerst als anwesend oder abwesend markiert werden."
                        else -> result.reason
                    }
                    _state.update {
                        it.copy(
                            isFinalizingOrReopening = false,
                            finalizeBlocked = FinalizeBlockedState(
                                reason = blockedMessage,
                                memberNames = memberNames
                            )
                        )
                    }
                }
                is FinalizeResult.Failure -> {
                    _state.update {
                        it.copy(isFinalizingOrReopening = false, error = result.cause.message)
                    }
                }
            }
        }
    }

    fun reopen() {
        val eventId = _state.value.event?.event?.id ?: return
        _state.update { it.copy(isFinalizingOrReopening = true) }
        viewModelScope.launch {
            attendanceRepository.reopen(eventId)
                .onSuccess {
                    _state.update { it.copy(isFinalizingOrReopening = false) }
                    loadEvent(eventId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isFinalizingOrReopening = false, error = e.message) }
                }
        }
    }

    fun dismissFinalizeBlocked() {
        _state.update { it.copy(finalizeBlocked = null) }
    }

    fun cancelEvent(scope: String = "this_only") {
        val eventId = _state.value.event?.event?.id ?: return
        viewModelScope.launch {
            eventRepository.cancelEvent(eventId, scope)
                .onSuccess {
                    loadEvent(eventId)
                    _events.emit(DetailEvent.Cancelled)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun uncancelEvent(scope: String = "this_only") {
        val eventId = _state.value.event?.event?.id ?: return
        viewModelScope.launch {
            eventRepository.uncancelEvent(eventId, scope)
                .onSuccess {
                    loadEvent(eventId)
                    _events.emit(DetailEvent.Uncancelled)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun loadReminderOverride(eventId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReminder = true) }
            notificationRepository.getReminderOverride(eventId)
                .onSuccess { override ->
                    _state.update { it.copy(reminderLeadMinutes = override.reminderLeadMinutes, isLoadingReminder = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingReminder = false) }
                }
        }
    }

    fun setReminderOverride(eventId: String, leadMinutes: Int?) {
        viewModelScope.launch {
            notificationRepository.setReminderOverride(eventId, leadMinutes)
                .onSuccess {
                    _state.update { it.copy(reminderLeadMinutes = leadMinutes) }
                }
        }
    }

    private fun checkCoachRole() {
        viewModelScope.launch {
            teamRepository.getMyRoles().onSuccess { roles ->
                val isCoachOrManager = roles.teamRoles.any { it.role == "coach" } ||
                    roles.clubRoles.any { it.role == "club_manager" }
                _state.update { it.copy(isCoach = isCoachOrManager) }
            }
        }
    }
}
