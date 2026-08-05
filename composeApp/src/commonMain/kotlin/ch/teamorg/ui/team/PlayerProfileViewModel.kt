package ch.teamorg.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.data.AttendanceStatsCalculator
import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.domain.CreateAbwesenheitRequest
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.UpdateAbwesenheitRequest
import ch.teamorg.preferences.UserPreferences
import ch.teamorg.repository.AbwesenheitRepository
import ch.teamorg.repository.AttendanceRepository
import ch.teamorg.repository.AuthRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.TeamRepository
import ch.teamorg.ui.components.deleteAccountErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProfileState(
    val member: TeamMember? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCoachOrManager: Boolean = false,
    val isOwnProfile: Boolean = false,
    val leftTeam: Boolean = false,
    val presencePct: Float = 0f,
    val trainingPresencePct: Float = 0f,
    val matchPresencePct: Float = 0f,
    val absenceRules: List<AbwesenheitRule> = emptyList(),
    val backfillStatus: String? = null,
    val showDeleteDialog: Boolean = false,
    val deleteInProgress: Boolean = false,
    val deleteError: String? = null,
    val accountDeleted: Boolean = false
)

class PlayerProfileViewModel(
    private val teamRepository: TeamRepository,
    private val userPreferences: UserPreferences,
    private val abwesenheitRepository: AbwesenheitRepository,
    private val attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerProfileState())
    val state = _state.asStateFlow()

    fun loadProfile(teamId: String, userId: String) {
        viewModelScope.launch {
            // Silent refresh when the profile is already loaded — loader only first time.
            _state.update { it.copy(isLoading = it.member == null, error = null) }
            val currentUserId = userPreferences.getUserId()
            _state.update { it.copy(isOwnProfile = currentUserId == userId) }

            teamRepository.getTeamRoster(teamId).onSuccess { roster ->
                val member = roster.firstOrNull { it.userId == userId }
                _state.update { it.copy(member = member, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load profile") }
            }

            teamRepository.getMyRoles().onSuccess { roles ->
                val isCoach = roles.teamRoles.any { it.role == "coach" } ||
                    roles.clubRoles.any { it.role == "club_manager" }
                _state.update { it.copy(isCoachOrManager = isCoach) }
            }

            loadAbsences()
            loadStats(userId)
        }
    }

    fun loadAbsences() {
        viewModelScope.launch {
            abwesenheitRepository.listRules()
                .onSuccess { rules ->
                    _state.update { it.copy(absenceRules = rules) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun loadStats(userId: String) {
        viewModelScope.launch {
            attendanceRepository.getRawAttendance(userId).onSuccess { responses ->
                val eventIds = responses.map { it.eventId }.toSet()
                val eventTypes = mutableMapOf<String, String>()
                eventRepository.getMyEvents().onSuccess { events ->
                    events.forEach { ewt ->
                        if (ewt.event.id in eventIds) {
                            eventTypes[ewt.event.id] = ewt.event.type
                        }
                    }
                }
                val stats = AttendanceStatsCalculator.calculateStats(responses, eventTypes)
                _state.update {
                    it.copy(
                        presencePct = stats.presencePct,
                        trainingPresencePct = stats.trainingPresencePct,
                        matchPresencePct = stats.matchPresencePct
                    )
                }
            }
        }
    }

    fun createAbsence(request: CreateAbwesenheitRequest) {
        viewModelScope.launch {
            abwesenheitRepository.createRule(request).onSuccess {
                loadAbsences()
                pollBackfillStatus()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Couldn't save absence rule. Try again.") }
            }
        }
    }

    fun updateAbsence(ruleId: String, request: UpdateAbwesenheitRequest) {
        viewModelScope.launch {
            abwesenheitRepository.updateRule(ruleId, request).onSuccess {
                loadAbsences()
                pollBackfillStatus()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Couldn't save absence rule. Try again.") }
            }
        }
    }

    fun deleteAbsence(ruleId: String) {
        viewModelScope.launch {
            abwesenheitRepository.deleteRule(ruleId).onSuccess {
                loadAbsences()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Failed to delete absence rule.") }
            }
        }
    }

    private fun pollBackfillStatus() {
        viewModelScope.launch {
            _state.update { it.copy(backfillStatus = "pending") }
            repeat(10) {
                delay(2000)
                abwesenheitRepository.getBackfillStatus().onSuccess { status ->
                    _state.update { it.copy(backfillStatus = status.status) }
                    if (status.status == "done" || status.status == "failed") return@launch
                }
            }
            // Give up after 20s
            _state.update { it.copy(backfillStatus = null) }
        }
    }

    fun updateJerseyNumber(teamId: String, userId: String, jersey: Int?) {
        viewModelScope.launch {
            teamRepository.updateMemberProfile(teamId, userId, jersey, _state.value.member?.position)
                .onSuccess { updated -> _state.update { it.copy(member = updated) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to update jersey") } }
        }
    }

    fun updatePosition(teamId: String, userId: String, position: String?) {
        viewModelScope.launch {
            teamRepository.updateMemberProfile(teamId, userId, _state.value.member?.jerseyNumber, position)
                .onSuccess { updated -> _state.update { it.copy(member = updated) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to update position") } }
        }
    }

    fun leaveTeam(teamId: String) {
        viewModelScope.launch {
            teamRepository.leaveTeam(teamId)
                .onSuccess { _state.update { it.copy(leftTeam = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to leave team") } }
        }
    }

    fun openDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = true, deleteError = null) }
    }

    fun closeDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false, deleteError = null) }
    }

    fun deleteAccount(password: String) {
        if (_state.value.deleteInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(deleteInProgress = true, deleteError = null) }
            when (val result = authRepository.deleteAccount(password)) {
                DeleteAccountResult.Success ->
                    _state.update {
                        it.copy(deleteInProgress = false, showDeleteDialog = false, accountDeleted = true)
                    }
                else ->
                    _state.update {
                        it.copy(deleteInProgress = false, deleteError = deleteAccountErrorMessage(result))
                    }
            }
        }
    }

    fun uploadAvatar(teamId: String, userId: String, imageBytes: ByteArray, extension: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            teamRepository.uploadAvatar(imageBytes, extension)
                .onSuccess { loadProfile(teamId, userId) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to upload avatar") } }
        }
    }
}
