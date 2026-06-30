package ch.teamorg.fake

import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.UserRoles
import ch.teamorg.repository.TeamRepository

class FakeTeamRepository : TeamRepository {

    var getRosterResult: Result<List<TeamMember>> = Result.success(emptyList())
    var removeMemberResult: Result<Unit> = Result.success(Unit)
    var createInviteResult: Result<String> = Result.success("https://teamorg.app/invite/abc123")

    var lastRosterTeamId: String? = null
    var lastRemovedTeamId: String? = null
    var lastRemovedUserId: String? = null
    var lastInviteTeamId: String? = null
    var lastInviteRole: String? = null

    fun reset() {
        getRosterResult = Result.success(emptyList())
        removeMemberResult = Result.success(Unit)
        createInviteResult = Result.success("https://teamorg.app/invite/abc123")
        lastRosterTeamId = null
        lastRemovedTeamId = null
        lastRemovedUserId = null
        lastInviteTeamId = null
        lastInviteRole = null
    }

    override suspend fun getTeamRoster(teamId: String): Result<List<TeamMember>> {
        lastRosterTeamId = teamId
        return getRosterResult
    }

    override suspend fun removeMember(teamId: String, userId: String): Result<Unit> {
        lastRemovedTeamId = teamId
        lastRemovedUserId = userId
        return removeMemberResult
    }

    override suspend fun createInvite(teamId: String, role: String, email: String?): Result<String> {
        lastInviteTeamId = teamId
        lastInviteRole = role
        return createInviteResult
    }

    var getMyRolesResult: Result<UserRoles> = Result.success(UserRoles())
    var updateMemberRoleResult: Result<TeamMember> = Result.success(
        TeamMember(userId = "u1", displayName = "Test", avatarUrl = null, role = "player", jerseyNumber = null, position = null)
    )
    var updateMemberProfileResult: Result<TeamMember> = Result.success(
        TeamMember(userId = "u1", displayName = "Test", avatarUrl = null, role = "player", jerseyNumber = null, position = null)
    )
    var leaveTeamResult: Result<Unit> = Result.success(Unit)
    var getSubGroupsResult: Result<List<SubGroup>> = Result.success(emptyList())
    var createSubGroupResult: Result<SubGroup> = Result.success(SubGroup(id = "sg1", teamId = "team1", name = "Group A"))
    var deleteSubGroupResult: Result<Unit> = Result.success(Unit)
    var addSubGroupMemberResult: Result<Unit> = Result.success(Unit)
    var removeSubGroupMemberResult: Result<Unit> = Result.success(Unit)
    var uploadAvatarResult: Result<Unit> = Result.success(Unit)

    override suspend fun getMyRoles(): Result<UserRoles> = getMyRolesResult

    override suspend fun updateMemberRole(teamId: String, userId: String, role: String): Result<TeamMember> = updateMemberRoleResult

    override suspend fun updateMemberProfile(teamId: String, userId: String, jerseyNumber: Int?, position: String?): Result<TeamMember> = updateMemberProfileResult

    override suspend fun leaveTeam(teamId: String): Result<Unit> = leaveTeamResult

    override suspend fun getSubGroups(teamId: String): Result<List<SubGroup>> = getSubGroupsResult

    override suspend fun createSubGroup(teamId: String, name: String): Result<SubGroup> = createSubGroupResult

    override suspend fun deleteSubGroup(teamId: String, subGroupId: String): Result<Unit> = deleteSubGroupResult

    override suspend fun addSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit> = addSubGroupMemberResult

    override suspend fun removeSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit> = removeSubGroupMemberResult

    override suspend fun uploadAvatar(imageBytes: ByteArray, extension: String): Result<Unit> = uploadAvatarResult

    var addMemberResult: Result<Unit> = Result.success(Unit)
    var updateRoleResult: Result<Unit> = Result.success(Unit)
    var linkNdsMemberResult: Result<Unit> = Result.success(Unit)

    override suspend fun addMember(teamId: String, userId: String, role: String): Result<Unit> = addMemberResult

    override suspend fun updateRole(teamId: String, userId: String, role: String): Result<Unit> = updateRoleResult

    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): Result<Unit> = linkNdsMemberResult
}
