package ch.teamorg.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.DuplicateSuggestion
import ch.teamorg.domain.LinkMemberResult
import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.TeamMember
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamRosterState(
    val members: List<TeamMember> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val inviteUrl: String? = null,
    val inviteSentTo: String? = null,
    val isClubManager: Boolean = false,
    val isCoachOrManager: Boolean = false,
    val clubId: String = "",
    val showEditTeamSheet: Boolean = false,
    val teamName: String = "",
    val teamDescription: String? = null,
    val subGroups: List<SubGroup> = emptyList(),
    val showSubGroupSheet: Boolean = false,
    val duplicates: List<DuplicateSuggestion> = emptyList(),
    val showDuplicatesSheet: Boolean = false,
    val mergeInProgress: Boolean = false,
    val mergeError: String? = null,
    val mergedCount: Int = 0
)

class TeamRosterViewModel(
    private val teamRepository: TeamRepository,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeamRosterState())
    val state = _state.asStateFlow()

    fun loadRoster(teamId: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.value = _state.value.copy(isRefreshing = true)
            } else {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }

            teamRepository.getTeamRoster(teamId).fold(
                onSuccess = { members ->
                    _state.value = _state.value.copy(
                        members = members,
                        isLoading = false,
                        isRefreshing = false
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to fetch roster"
                    )
                }
            )
            checkClubManagerRole(teamId)
        }
    }

    private fun checkClubManagerRole(teamId: String) {
        viewModelScope.launch {
            teamRepository.getMyRoles().onSuccess { roles ->
                val isClubManager = roles.clubRoles.any { it.role == "club_manager" }
                val isCoach = roles.teamRoles.any { it.teamId == teamId && it.role == "coach" }
                val clubId = roles.teamRoles.firstOrNull { it.teamId == teamId }?.clubId
                    ?: roles.clubRoles.firstOrNull()?.clubId
                    ?: ""
                _state.value = _state.value.copy(
                    isClubManager = isClubManager,
                    isCoachOrManager = isClubManager || isCoach,
                    clubId = clubId
                )
                if (isClubManager || isCoach) loadDuplicateSuggestions(teamId)
            }
        }
    }

    private fun loadDuplicateSuggestions(teamId: String) {
        viewModelScope.launch {
            teamRepository.getDuplicateSuggestions(teamId).onSuccess { suggestions ->
                _state.value = _state.value.copy(duplicates = suggestions)
            }
        }
    }

    fun promoteMember(teamId: String, userId: String) {
        viewModelScope.launch {
            teamRepository.updateMemberRole(teamId, userId, "coach").fold(
                onSuccess = { updated ->
                    _state.value = _state.value.copy(
                        members = _state.value.members.map { m ->
                            if (m.userId == userId) updated else m
                        }
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to promote member")
                }
            )
        }
    }

    fun editTeam(teamId: String, name: String, description: String?) {
        viewModelScope.launch {
            clubRepository.updateTeam(teamId, name, description).fold(
                onSuccess = { updated ->
                    _state.value = _state.value.copy(
                        teamName = updated.name,
                        showEditTeamSheet = false
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to update team")
                }
            )
        }
    }

    fun showEditTeamSheet() {
        _state.value = _state.value.copy(showEditTeamSheet = true)
    }

    fun hideEditTeamSheet() {
        _state.value = _state.value.copy(showEditTeamSheet = false)
    }

    fun removeMember(teamId: String, userId: String) {
        viewModelScope.launch {
            teamRepository.removeMember(teamId, userId).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        members = _state.value.members.filterNot { it.userId == userId }
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to remove member")
                }
            )
        }
    }

    fun createInvite(teamId: String, role: String, email: String? = null) {
        val invitedEmail = email?.trim()?.ifBlank { null }
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            teamRepository.createInvite(teamId, role, invitedEmail).fold(
                onSuccess = { url ->
                    // Email present → personal invite was emailed; otherwise show the shareable link.
                    _state.value = if (invitedEmail != null) {
                        _state.value.copy(inviteSentTo = invitedEmail)
                    } else {
                        _state.value.copy(inviteUrl = url)
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to create invite")
                }
            )
        }
    }

    fun resetInvite() {
        _state.value = _state.value.copy(inviteUrl = null, inviteSentTo = null)
    }

    fun loadSubGroups(teamId: String) {
        viewModelScope.launch {
            teamRepository.getSubGroups(teamId).onSuccess { groups ->
                _state.value = _state.value.copy(subGroups = groups)
            }
        }
    }

    fun createSubGroup(teamId: String, name: String) {
        viewModelScope.launch {
            teamRepository.createSubGroup(teamId, name).onSuccess { group ->
                _state.value = _state.value.copy(subGroups = _state.value.subGroups + group)
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "Failed to create sub-group")
            }
        }
    }

    fun deleteSubGroup(teamId: String, subGroupId: String) {
        viewModelScope.launch {
            teamRepository.deleteSubGroup(teamId, subGroupId).onSuccess {
                _state.value = _state.value.copy(
                    subGroups = _state.value.subGroups.filterNot { it.id == subGroupId }
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "Failed to delete sub-group")
            }
        }
    }

    fun toggleSubGroupSheet() {
        _state.value = _state.value.copy(showSubGroupSheet = !_state.value.showSubGroupSheet)
    }

    fun openDuplicatesSheet() {
        _state.value = _state.value.copy(showDuplicatesSheet = true, mergeError = null)
    }

    fun closeDuplicatesSheet() {
        _state.value = _state.value.copy(showDuplicatesSheet = false, mergeError = null)
    }

    fun clearMergeError() {
        _state.value = _state.value.copy(mergeError = null)
    }

    /**
     * Merges [userId]'s account into imported roster row [memberId]. Irreversible: the placeholder
     * account is deleted server-side. On failure the sheet stays open so another account can be picked.
     */
    fun mergeDuplicate(teamId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mergeInProgress = true, mergeError = null)
            when (teamRepository.linkNdsMember(teamId, memberId, userId)) {
                LinkMemberResult.Success -> {
                    _state.value = _state.value.copy(
                        duplicates = _state.value.duplicates.filterNot { it.memberId == memberId },
                        mergedCount = _state.value.mergedCount + 1,
                        mergeInProgress = false
                    )
                    loadRoster(teamId, isRefresh = true)
                }
                LinkMemberResult.Conflict -> setMergeError(
                    "This account is already linked to another member of this team."
                )
                LinkMemberResult.NotLinkable -> setMergeError("This account can't be linked.")
                is LinkMemberResult.Error -> setMergeError("Couldn't merge. Please try again.")
            }
        }
    }

    private fun setMergeError(message: String) {
        _state.value = _state.value.copy(mergeInProgress = false, mergeError = message)
    }
}
