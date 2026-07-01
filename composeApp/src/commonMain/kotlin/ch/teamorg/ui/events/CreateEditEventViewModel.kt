package ch.teamorg.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.CreateEventRequest
import ch.teamorg.domain.EditEventRequest
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.domain.RecurringPattern
import ch.teamorg.domain.SubGroup
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class CreateEditEventState(
    // Form fields
    val title: String = "",
    val type: String = "training",
    val startDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val startTime: LocalTime = LocalTime(18, 0),
    val endDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val endTime: LocalTime = LocalTime(19, 30),
    val meetupEnabled: Boolean = false,
    val meetupTime: LocalTime = LocalTime(17, 30),
    val location: String = "",
    val description: String = "",
    val minAttendeesEnabled: Boolean = false,
    val minAttendees: Int = 0,
    val selectedTeamIds: Set<String> = emptySet(),
    val selectedSubgroupIds: Set<String> = emptySet(),
    val recurringEnabled: Boolean = false,
    val recurringPattern: RecurringPatternState? = null,
    val defaultResponse: String = "none",  // "none"|"accepted"|"declined"

    // UI state
    val isEditMode: Boolean = false,
    val editEventId: String? = null,
    val isSeriesEvent: Boolean = false,
    val availableTeams: List<MatchedTeam> = emptyList(),
    val availableSubgroups: List<SubGroup> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,

    // Validation
    val titleError: String? = null,
    val endTimeError: String? = null,
    val teamError: String? = null,

    // Timezone info label
    val timezoneLabel: String = TimeZone.currentSystemDefault().id
)

data class RecurringPatternState(
    val patternType: String = "weekly",  // "daily" | "weekly" | "custom"
    val weekdays: Set<Int> = emptySet(), // 0=Mon..6=Sun
    val intervalDays: Int = 7,
    val hasEndDate: Boolean = false,
    val endDate: LocalDate? = null
)

sealed class FormEvent {
    data object SaveSuccess : FormEvent()
    data object CancelSuccess : FormEvent()
}

class CreateEditEventViewModel(
    private val eventRepository: EventRepository,
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEditEventState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<FormEvent>()
    val events = _events.asSharedFlow()

    init {
        loadAvailableTeams()
    }

    fun resetForm() {
        _state.value = CreateEditEventState()
        loadAvailableTeams()
    }

    private fun loadAvailableTeams() {
        viewModelScope.launch {
            var loaded = false
            teamRepository.getMyRoles().onSuccess { roles ->
                val clubIds = (roles.clubRoles.map { it.clubId } +
                    roles.teamRoles.map { it.clubId }).distinct()
                val teams = mutableListOf<MatchedTeam>()
                for (clubId in clubIds) {
                    clubRepository.getClubTeams(clubId).onSuccess { clubTeams ->
                        teams.addAll(clubTeams.map { MatchedTeam(it.id, it.name) })
                    }
                }
                if (teams.isNotEmpty()) {
                    _state.update { it.copy(availableTeams = teams.distinctBy { t -> t.id }) }
                    loaded = true
                }
            }
            // Fallback: derive teams from cached events
            if (!loaded) {
                eventRepository.getMyEvents().onSuccess { cached ->
                    val teams = cached.flatMap { it.matchedTeams }.distinctBy { it.id }
                    if (_state.value.availableTeams.isEmpty()) {
                        _state.update { it.copy(availableTeams = teams) }
                    }
                }
            }
        }
    }

    fun setTitle(title: String) {
        _state.update { it.copy(title = title, titleError = null) }
    }

    fun setType(type: String) {
        _state.update { it.copy(type = type) }
    }

    fun setStartDate(date: LocalDate) {
        _state.update {
            val newEndDate = if (it.endDate == it.startDate) date else it.endDate
            it.copy(startDate = date, endDate = newEndDate)
        }
    }

    fun setStartTime(time: LocalTime) {
        _state.update { it.copy(startTime = time, endTimeError = null) }
    }

    fun setEndDate(date: LocalDate) {
        _state.update { it.copy(endDate = date, endTimeError = null) }
    }

    fun setEndTime(time: LocalTime) {
        _state.update { it.copy(endTime = time, endTimeError = null) }
    }

    fun toggleMeetup(enabled: Boolean) {
        _state.update { it.copy(meetupEnabled = enabled) }
    }

    fun setMeetupTime(time: LocalTime) {
        _state.update { it.copy(meetupTime = time) }
    }

    fun setLocation(location: String) {
        _state.update { it.copy(location = location) }
    }

    fun setDescription(desc: String) {
        _state.update { it.copy(description = desc) }
    }

    fun toggleMinAttendees(enabled: Boolean) {
        _state.update {
            it.copy(
                minAttendeesEnabled = enabled,
                minAttendees = if (enabled && it.minAttendees < 1) 6 else it.minAttendees
            )
        }
    }

    fun setMinAttendees(count: Int) {
        _state.update { it.copy(minAttendees = count) }
    }

    fun toggleTeam(teamId: String) {
        _state.update {
            val newSet = if (teamId in it.selectedTeamIds) {
                it.selectedTeamIds - teamId
            } else {
                it.selectedTeamIds + teamId
            }
            it.copy(selectedTeamIds = newSet, teamError = null)
        }
        loadSubgroupsForSelectedTeams()
    }

    fun toggleSubgroup(subgroupId: String) {
        _state.update {
            val newSet = if (subgroupId in it.selectedSubgroupIds) {
                it.selectedSubgroupIds - subgroupId
            } else {
                it.selectedSubgroupIds + subgroupId
            }
            it.copy(selectedSubgroupIds = newSet)
        }
    }

    fun setDefaultResponse(value: String) {
        _state.update { it.copy(defaultResponse = value) }
    }

    fun setRecurringEnabled(enabled: Boolean) {
        _state.update { it.copy(recurringEnabled = enabled) }
    }

    fun setRecurringPattern(pattern: RecurringPatternState) {
        _state.update { it.copy(recurringPattern = pattern) }
    }

    fun loadForEdit(eventId: String, isDuplicate: Boolean = false) {
        viewModelScope.launch {
            eventRepository.getEventDetail(eventId).onSuccess { ewt ->
                val e = ewt.event
                val tz = TimeZone.currentSystemDefault()
                val startLdt = e.startAt.toLocalDateTime(tz)
                val endLdt = e.endAt.toLocalDateTime(tz)
                _state.update {
                    it.copy(
                        isEditMode = !isDuplicate,
                        editEventId = if (isDuplicate) null else eventId,
                        title = e.title,
                        type = e.type,
                        startDate = startLdt.date,
                        startTime = startLdt.time,
                        endDate = endLdt.date,
                        endTime = endLdt.time,
                        meetupEnabled = e.meetupAt != null,
                        meetupTime = e.meetupAt?.toLocalDateTime(tz)?.time ?: LocalTime(17, 30),
                        location = e.location ?: "",
                        description = e.description ?: "",
                        minAttendeesEnabled = e.minAttendees != null,
                        minAttendees = e.minAttendees ?: 0,
                        selectedTeamIds = e.teamIds.toSet(),
                        selectedSubgroupIds = e.subgroupIds.toSet(),
                        isSeriesEvent = if (isDuplicate) false else e.seriesId != null,
                        recurringEnabled = false,
                        defaultResponse = e.defaultResponse,
                        availableTeams = ewt.matchedTeams
                    )
                }
                loadSubgroupsForSelectedTeams()
            }
        }
    }

    fun loadForDuplicate(eventId: String) {
        loadForEdit(eventId, isDuplicate = true)
    }

    fun save(scope: String? = null) {
        if (!validate()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            val s = _state.value
            val tz = TimeZone.currentSystemDefault()
            val startInstant = LocalDateTime(s.startDate, s.startTime).toInstant(tz)
            val endInstant = LocalDateTime(s.endDate, s.endTime).toInstant(tz)
            val meetupInstant = if (s.meetupEnabled) {
                LocalDateTime(s.startDate, s.meetupTime).toInstant(tz)
            } else null

            if (s.isEditMode && s.editEventId != null) {
                val request = EditEventRequest(
                    title = s.title,
                    type = s.type,
                    startAt = startInstant,
                    endAt = endInstant,
                    meetupAt = meetupInstant,
                    location = s.location.ifBlank { null },
                    description = s.description.ifBlank { null },
                    minAttendees = if (s.minAttendeesEnabled) s.minAttendees else null,
                    teamIds = s.selectedTeamIds.toList(),
                    subgroupIds = s.selectedSubgroupIds.toList(),
                    scope = scope,
                    defaultResponse = s.defaultResponse
                )
                eventRepository.editEvent(s.editEventId, request)
                    .onSuccess {
                        _state.update { st -> st.copy(isSaving = false) }
                        _events.emit(FormEvent.SaveSuccess)
                    }
                    .onFailure { err -> _state.update { st -> st.copy(saveError = err.message, isSaving = false) } }
            } else {
                val recurring = if (s.recurringEnabled && s.recurringPattern != null) {
                    val p = s.recurringPattern
                    RecurringPattern(
                        patternType = p.patternType,
                        weekdays = p.weekdays.toList().takeIf { it.isNotEmpty() },
                        intervalDays = p.intervalDays.takeIf { p.patternType == "custom" },
                        seriesEndDate = p.endDate?.toString()
                    )
                } else null
                val request = CreateEventRequest(
                    title = s.title,
                    type = s.type,
                    startAt = startInstant,
                    endAt = endInstant,
                    meetupAt = meetupInstant,
                    location = s.location.ifBlank { null },
                    description = s.description.ifBlank { null },
                    minAttendees = if (s.minAttendeesEnabled) s.minAttendees else null,
                    teamIds = s.selectedTeamIds.toList(),
                    subgroupIds = s.selectedSubgroupIds.toList(),
                    recurring = recurring,
                    defaultResponse = s.defaultResponse
                )
                eventRepository.createEvent(request)
                    .onSuccess {
                        _state.update { st -> st.copy(isSaving = false) }
                        _events.emit(FormEvent.SaveSuccess)
                    }
                    .onFailure { err -> _state.update { st -> st.copy(saveError = err.message, isSaving = false) } }
            }
        }
    }

    fun cancelEvent(scope: String = "this_only") {
        val eventId = _state.value.editEventId ?: return
        viewModelScope.launch {
            eventRepository.cancelEvent(eventId, scope)
                .onSuccess { _events.emit(FormEvent.CancelSuccess) }
                .onFailure { err -> _state.update { it.copy(saveError = err.message) } }
        }
    }

    private fun validate(): Boolean {
        val s = _state.value
        var valid = true

        if (s.title.isBlank()) {
            _state.update { it.copy(titleError = "Title is required") }
            valid = false
        }

        val tz = TimeZone.currentSystemDefault()
        val startInstant = LocalDateTime(s.startDate, s.startTime).toInstant(tz)
        val endInstant = LocalDateTime(s.endDate, s.endTime).toInstant(tz)
        if (endInstant <= startInstant) {
            _state.update { it.copy(endTimeError = "End time must be after start time") }
            valid = false
        }

        if (s.selectedTeamIds.isEmpty()) {
            _state.update { it.copy(teamError = "Select at least one team") }
            valid = false
        }

        return valid
    }

    private fun loadSubgroupsForSelectedTeams() {
        val teamIds = _state.value.selectedTeamIds
        if (teamIds.isEmpty()) {
            _state.update { it.copy(availableSubgroups = emptyList()) }
            return
        }
        viewModelScope.launch {
            val allSubgroups = mutableListOf<SubGroup>()
            for (teamId in teamIds) {
                eventRepository.getSubGroups(teamId).onSuccess { subgroups ->
                    allSubgroups.addAll(subgroups)
                }
            }
            _state.update { it.copy(availableSubgroups = allSubgroups) }
        }
    }
}
