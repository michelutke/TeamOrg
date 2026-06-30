package ch.teamorg.ui.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.ClubUser
import ch.teamorg.domain.Team
import ch.teamorg.domain.TeamMember
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 25

data class ClubMembersState(
    val users: List<ClubUser> = emptyList(),
    val teams: List<Team> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val filter: String = "",
    val inviteSentTo: String? = null
)

class ClubMembersViewModel(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClubMembersState())
    val state = _state.asStateFlow()

    private var clubId: String = ""
    private var offset: Int = 0

    fun init(clubId: String) {
        if (this.clubId == clubId) return
        this.clubId = clubId
        offset = 0
        _state.update { ClubMembersState() }
        loadTeams()
        loadMore()
    }

    private fun loadTeams() {
        viewModelScope.launch {
            clubRepository.getClubTeams(clubId).onSuccess { teams ->
                _state.update { it.copy(teams = teams) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            clubRepository.listClubUsers(clubId, PAGE_SIZE, offset).fold(
                onSuccess = { page ->
                    offset += page.size
                    _state.update { it.copy(
                        users = it.users + page,
                        loading = false,
                        endReached = page.size < PAGE_SIZE
                    ) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load members") }
                }
            )
        }
    }

    fun setFilter(query: String) {
        _state.update { it.copy(filter = query) }
    }

    fun addToTeam(teamId: String, userId: String, role: String) {
        viewModelScope.launch {
            teamRepository.addMember(teamId, userId, role).onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Failed to add member") }
            }
        }
    }

    fun changeRole(teamId: String, userId: String, role: String) {
        viewModelScope.launch {
            teamRepository.updateMemberRole(teamId, userId, role).fold(
                onSuccess = { updated ->
                    _state.update { state ->
                        state.copy(users = state.users.map { user ->
                            if (user.userId == userId) {
                                user.copy(teamRoles = user.teamRoles.map { ref ->
                                    if (ref.teamId == teamId) ref.copy(role = updated.role) else ref
                                })
                            } else user
                        })
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to change role") }
                }
            )
        }
    }

    fun remove(teamId: String, userId: String) {
        viewModelScope.launch {
            teamRepository.removeMember(teamId, userId).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(users = state.users.map { user ->
                            if (user.userId == userId) {
                                user.copy(teamRoles = user.teamRoles.filterNot { it.teamId == teamId })
                            } else user
                        })
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to remove member") }
                }
            )
        }
    }

    fun invite(teamId: String, role: String, email: String) {
        val trimmed = email.trim().ifBlank { null } ?: return
        viewModelScope.launch {
            teamRepository.createInvite(teamId, role, trimmed).fold(
                onSuccess = { _ ->
                    _state.update { it.copy(inviteSentTo = trimmed) }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to send invite") }
                }
            )
        }
    }

    fun linkNds(teamId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            teamRepository.linkNdsMember(teamId, memberId, userId).onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Failed to link NDS member") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInviteSent() {
        _state.update { it.copy(inviteSentTo = null) }
    }
}
