package ch.teamorg.domain.repositories

import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamMember
import java.util.UUID

interface TeamRepository {
    suspend fun create(clubId: UUID, name: String, description: String?): Team
    suspend fun findById(id: UUID): Team?
    suspend fun update(
        id: UUID,
        name: String?,
        description: String?,
        appearanceShape: String? = null,
        appearanceColor: String? = null
    ): Team
    suspend fun archive(id: UUID): Team
    suspend fun listMembers(teamId: UUID): List<TeamMember>
    suspend fun hasRole(userId: UUID, teamId: UUID, vararg roles: String): Boolean
    suspend fun getClubId(teamId: UUID): UUID?
    suspend fun updateMemberRole(teamId: UUID, userId: UUID, newRole: String): TeamMember
    suspend fun removeMember(teamId: UUID, userId: UUID)
    suspend fun getUserClubRoles(userId: UUID): List<Pair<UUID, String>>
    suspend fun getUserTeamRoles(userId: UUID): List<Triple<UUID, UUID, String>>
    suspend fun updateMemberProfile(teamId: UUID, userId: UUID, jerseyNumber: Int?, position: String?): TeamMember
}
