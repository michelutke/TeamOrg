package ch.teamorg.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.preferences.UserPreferences
import ch.teamorg.repository.AttendanceRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class EventViewMode { LIST, CALENDAR }

data class EventAttendanceCounts(
    val confirmedCount: Int = 0,
    val maybeCount: Int = 0,
    val declinedCount: Int = 0,
    val myResponse: String? = null
)

data class EventListState(
    val allEvents: List<EventWithTeams> = emptyList(),
    val events: List<EventWithTeams> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTeamIds: Set<String> = emptySet(),
    val selectedTypes: Set<String> = emptySet(),
    val showAwaitingOnly: Boolean = false,
    val isCoach: Boolean = false,
    val teams: List<MatchedTeam> = emptyList(),
    val viewMode: EventViewMode = EventViewMode.LIST,
    val eventsByDate: Map<LocalDate, List<EventWithTeams>> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val selectedDayEvents: List<EventWithTeams> = emptyList(),
    val attendanceCounts: Map<String, EventAttendanceCounts> = emptyMap()
)

class EventListViewModel(
    private val eventRepository: EventRepository,
    private val teamRepository: TeamRepository,
    private val userPreferences: UserPreferences,
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EventListState())
    val state = _state.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            eventRepository.getMyEvents().onSuccess { events ->
                val sorted = events.sortedBy { it.event.startAt }
                val teams = sorted.flatMap { it.matchedTeams }.distinctBy { it.id }
                _state.update { it.copy(allEvents = sorted, isLoading = false, teams = teams) }
                applyFilters()
                checkCoachRole()
                loadAttendanceCounts(sorted.map { it.event.id })
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun applyFilters() {
        val current = _state.value
        val filtered = current.allEvents.filter { ewt ->
            val matchesTeam = current.selectedTeamIds.isEmpty() ||
                ewt.matchedTeams.any { it.id in current.selectedTeamIds }
            val matchesType = current.selectedTypes.isEmpty() ||
                ewt.event.type in current.selectedTypes
            val matchesAwaiting = !current.showAwaitingOnly ||
                ewt.event.checkInStatus == "awaiting_checkin"
            matchesTeam && matchesType && matchesAwaiting
        }
        val byDate = buildDateMap(filtered)
        _state.update {
            it.copy(
                events = filtered,
                eventsByDate = byDate,
                selectedDayEvents = if (it.selectedDate != null)
                    byDate[it.selectedDate] ?: emptyList()
                else emptyList()
            )
        }
    }

    private fun buildDateMap(events: List<EventWithTeams>): Map<LocalDate, List<EventWithTeams>> {
        val tz = TimeZone.currentSystemDefault()
        val byDate = mutableMapOf<LocalDate, MutableList<EventWithTeams>>()
        events.forEach { ewt ->
            val startDate = ewt.event.startAt.toLocalDateTime(tz).date
            val endDate = ewt.event.endAt.toLocalDateTime(tz).date
            var date = startDate
            while (date <= endDate) {
                byDate.getOrPut(date) { mutableListOf() }.add(ewt)
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        return byDate
    }

    private fun loadAttendanceCounts(eventIds: List<String>) {
        viewModelScope.launch {
            val countsMap = mutableMapOf<String, EventAttendanceCounts>()
            val userId = userPreferences.getUserId()
            eventIds.forEach { eventId ->
                attendanceRepository.getEventAttendance(eventId).onSuccess { responses ->
                    val confirmed = responses.count { it.status == "confirmed" }
                    val maybe = responses.count { it.status == "unsure" }
                    val declined = responses.count {
                        it.status == "declined" || it.status == "declined-auto"
                    }
                    val myResponse = responses.find { it.userId == userId }?.status
                    countsMap[eventId] = EventAttendanceCounts(
                        confirmedCount = confirmed,
                        maybeCount = maybe,
                        declinedCount = declined,
                        myResponse = myResponse
                    )
                }
            }
            _state.update { it.copy(attendanceCounts = it.attendanceCounts + countsMap) }
        }
    }

    fun submitResponse(eventId: String, status: String, reason: String?) {
        // Optimistic update
        _state.update { state ->
            val counts = state.attendanceCounts.toMutableMap()
            val current = counts[eventId] ?: EventAttendanceCounts()
            counts[eventId] = current.copy(myResponse = status)
            state.copy(attendanceCounts = counts)
        }
        viewModelScope.launch {
            val request = ch.teamorg.domain.SubmitResponseRequest(status = status, reason = reason)
            attendanceRepository.submitResponse(eventId, request)
                .onSuccess {
                    // Reload counts to get accurate numbers
                    loadAttendanceCounts(listOf(eventId))
                }
                .onFailure {
                    // Revert optimistic update
                    _state.update { state ->
                        val counts = state.attendanceCounts.toMutableMap()
                        val current = counts[eventId] ?: EventAttendanceCounts()
                        counts[eventId] = current.copy(myResponse = null)
                        state.copy(attendanceCounts = counts)
                    }
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

    fun toggleTeamFilter(teamId: String) {
        _state.update {
            val newSet = if (teamId in it.selectedTeamIds)
                it.selectedTeamIds - teamId
            else
                it.selectedTeamIds + teamId
            it.copy(selectedTeamIds = newSet)
        }
        applyFilters()
    }

    fun clearTeamFilters() {
        _state.update { it.copy(selectedTeamIds = emptySet()) }
        applyFilters()
    }

    fun toggleTypeFilter(type: String) {
        _state.update {
            val newSet = if (type in it.selectedTypes)
                it.selectedTypes - type
            else
                it.selectedTypes + type
            it.copy(selectedTypes = newSet)
        }
        applyFilters()
    }

    fun clearTypeFilters() {
        _state.update { it.copy(selectedTypes = emptySet()) }
        applyFilters()
    }

    fun toggleAwaitingFilter() {
        _state.update { it.copy(showAwaitingOnly = !it.showAwaitingOnly) }
        applyFilters()
    }

    fun setViewMode(mode: EventViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun selectDate(date: LocalDate) {
        val events = _state.value.eventsByDate[date] ?: emptyList()
        _state.update { it.copy(selectedDate = date, selectedDayEvents = events) }
    }
}
