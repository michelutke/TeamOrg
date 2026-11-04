package ch.teamorg.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.NotificationSettings
import ch.teamorg.domain.UpdateNotificationSettingsRequest
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.NotificationRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamInfo(val teamId: String, val teamName: String, val isCoach: Boolean)

data class SettingsState(
    val teams: List<TeamInfo> = emptyList(),
    val selectedTeamId: String? = null,
    val settings: NotificationSettings? = null,
    val isCoach: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

class NotificationSettingsViewModel(
    private val notificationRepository: NotificationRepository,
    private val teamRepository: TeamRepository,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val debounceJobs = mutableMapOf<Pair<String, String>, Job>()

    init {
        loadTeams()
    }

    fun loadTeams() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            teamRepository.getMyRoles()
                .onSuccess { roles ->
                    val clubIds = roles.teamRoles.map { it.clubId }.distinct()
                    val teamNameMap = mutableMapOf<String, String>()
                    for (clubId in clubIds) {
                        clubRepository.getClubTeams(clubId).onSuccess { teams ->
                            for (team in teams) {
                                teamNameMap[team.id] = team.name
                            }
                        }
                    }
                    val teams = roles.teamRoles.map { role ->
                        TeamInfo(
                            teamId = role.teamId,
                            teamName = teamNameMap[role.teamId] ?: role.teamId,
                            isCoach = role.role == "coach"
                        )
                    }
                    val firstTeamId = teams.firstOrNull()?.teamId
                    val isCoach = roles.teamRoles.any { it.role == "coach" } ||
                        roles.clubRoles.any { it.role == "club_manager" }
                    _state.update { it.copy(teams = teams, selectedTeamId = firstTeamId, isCoach = isCoach) }
                    if (firstTeamId != null) {
                        loadSettings(firstTeamId)
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun loadSettings(teamId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            notificationRepository.getSettings(teamId)
                .onSuccess { settings ->
                    _state.update { it.copy(settings = settings, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun selectTeam(teamId: String) {
        val team = _state.value.teams.firstOrNull { it.teamId == teamId } ?: return
        _state.update { it.copy(selectedTeamId = teamId, isCoach = team.isCoach) }
        loadSettings(teamId)
    }

    fun updateSetting(field: String, value: Any) {
        val teamId = _state.value.selectedTeamId ?: return
        val current = _state.value.settings ?: return

        // Optimistic update
        val updated = when (field) {
            "eventsNew" -> current.copy(eventsNew = value as Boolean)
            "eventsEdit" -> current.copy(eventsEdit = value as Boolean)
            "eventsCancel" -> current.copy(eventsCancel = value as Boolean)
            "remindersEnabled" -> current.copy(remindersEnabled = value as Boolean)
            "reminderLeadMinutes" -> current.copy(reminderLeadMinutes = value as Int)
            "coachResponseMode" -> current.copy(coachResponseMode = value as String)
            "absencesEnabled" -> current.copy(absencesEnabled = value as Boolean)
            else -> current
        }
        _state.update { it.copy(settings = updated) }

        val debounceKey = teamId to field
        debounceJobs[debounceKey]?.cancel()
        debounceJobs[debounceKey] = viewModelScope.launch {
            delay(300)
            val request = buildRequest(field, value)
            notificationRepository.updateSettings(teamId, request)
                .onFailure {
                    // Revert on failure
                    _state.update { s ->
                        s.copy(
                            settings = current,
                            error = "Settings not saved. Try again."
                        )
                    }
                }
        }
    }

    private fun buildRequest(field: String, value: Any): UpdateNotificationSettingsRequest = when (field) {
        "eventsNew" -> UpdateNotificationSettingsRequest(eventsNew = value as Boolean)
        "eventsEdit" -> UpdateNotificationSettingsRequest(eventsEdit = value as Boolean)
        "eventsCancel" -> UpdateNotificationSettingsRequest(eventsCancel = value as Boolean)
        "remindersEnabled" -> UpdateNotificationSettingsRequest(remindersEnabled = value as Boolean)
        "reminderLeadMinutes" -> UpdateNotificationSettingsRequest(reminderLeadMinutes = value as Int)
        "coachResponseMode" -> UpdateNotificationSettingsRequest(coachResponseMode = value as String)
        "absencesEnabled" -> UpdateNotificationSettingsRequest(absencesEnabled = value as Boolean)
        else -> UpdateNotificationSettingsRequest()
    }
}
