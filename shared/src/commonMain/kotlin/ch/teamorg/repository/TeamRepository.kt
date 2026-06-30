package ch.teamorg.repository

import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.UserRoles

interface TeamRepository {
    suspend fun getTeamRoster(teamId: String): Result<List<TeamMember>>
    suspend fun addMember(teamId: String, userId: String, role: String): Result<Unit>
    suspend fun removeMember(teamId: String, userId: String): Result<Unit>
    suspend fun createInvite(teamId: String, role: String, email: String?): Result<String>
    suspend fun getMyRoles(): Result<UserRoles>
    suspend fun updateMemberRole(teamId: String, userId: String, role: String): Result<TeamMember>
    suspend fun updateRole(teamId: String, userId: String, role: String): Result<Unit>
    suspend fun updateMemberProfile(teamId: String, userId: String, jerseyNumber: Int?, position: String?): Result<TeamMember>
    suspend fun leaveTeam(teamId: String): Result<Unit>
    suspend fun getSubGroups(teamId: String): Result<List<SubGroup>>
    suspend fun createSubGroup(teamId: String, name: String): Result<SubGroup>
    suspend fun deleteSubGroup(teamId: String, subGroupId: String): Result<Unit>
    suspend fun addSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit>
    suspend fun removeSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit>
    suspend fun uploadAvatar(imageBytes: ByteArray, extension: String): Result<Unit>
    suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): Result<Unit>
}
