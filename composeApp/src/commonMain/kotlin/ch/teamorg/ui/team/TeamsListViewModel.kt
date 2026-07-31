package ch.teamorg.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.Club
import ch.teamorg.domain.Team
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamsListState(
    val teams: List<Team> = emptyList(),
    val club: Club? = null,
    val clubId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateSheet: Boolean = false,
    val showEditClubSheet: Boolean = false,
    val isClubManager: Boolean = false
)

class TeamsListViewModel(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeamsListState())
    val state = _state.asStateFlow()

    init {
        loadTeams()
    }

    fun loadTeams() {
        viewModelScope.launch {
            // Silent refresh when cached data exists — loader only on first load.
            _state.update { it.copy(isLoading = it.teams.isEmpty() && it.club == null, error = null) }
            teamRepository.getMyRoles().onSuccess { roles ->
                val clubRole = roles.clubRoles.firstOrNull()
                // Derive clubId from club roles first, fall back to team roles
                val clubId = clubRole?.clubId
                    ?: roles.teamRoles.firstOrNull()?.clubId
                val isManager = clubRole?.role == "club_manager"

                if (clubId != null) {
                    _state.update {
                        it.copy(clubId = clubId, isClubManager = isManager)
                    }
                    clubRepository.getClub(clubId).onSuccess { club ->
                        _state.update { it.copy(club = club) }
                    }
                    clubRepository.getClubTeams(clubId).onSuccess { teams ->
                        _state.update { it.copy(teams = teams, isLoading = false) }
                    }.onFailure { e ->
                        _state.update { it.copy(error = e.message, isLoading = false) }
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun createTeam(name: String, description: String?) {
        val clubId = _state.value.clubId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, showCreateSheet = false) }
            clubRepository.createTeam(clubId, name, description).onSuccess {
                loadTeams()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun showCreateSheet() {
        _state.update { it.copy(showCreateSheet = true) }
    }

    fun hideCreateSheet() {
        _state.update { it.copy(showCreateSheet = false) }
    }

    fun showEditClubSheet() {
        _state.update { it.copy(showEditClubSheet = true) }
    }

    fun hideEditClubSheet() {
        _state.update { it.copy(showEditClubSheet = false) }
    }

    fun updateClub(name: String, location: String?) {
        val clubId = _state.value.clubId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showEditClubSheet = false) }
            clubRepository.updateClub(clubId, name, location)
                .onSuccess { club -> _state.update { it.copy(club = club, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
        }
    }
}
