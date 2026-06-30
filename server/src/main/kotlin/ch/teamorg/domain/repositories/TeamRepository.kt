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
    /**
     * Migrate a deprecated team into a live successor: copies team_roles and per-member
     * notification_settings (skipping rows that already exist on the target), carries over
     * games_sync_enabled when the target is still default, sets the target's predecessor lineage,
     * and archives the source. Runs in a single transaction. Returns the number of members moved
     * (team_roles rows newly inserted on the target).
     */
    suspend fun migrateTeam(sourceTeamId: UUID, targetTeamId: UUID): Int
    suspend fun listMembers(teamId: UUID): List<TeamMember>
    suspend fun hasRole(userId: UUID, teamId: UUID, vararg roles: String): Boolean
    suspend fun getClubId(teamId: UUID): UUID?
    suspend fun setGamesSyncEnabled(teamId: UUID, enabled: Boolean)
    suspend fun addMember(teamId: UUID, userId: UUID, role: String): TeamMember
    suspend fun updateMemberRole(teamId: UUID, userId: UUID, newRole: String): TeamMember
    suspend fun removeMember(teamId: UUID, userId: UUID)
    suspend fun getUserClubRoles(userId: UUID): List<Pair<UUID, String>>
    suspend fun getUserTeamRoles(userId: UUID): List<Triple<UUID, UUID, String>>
    suspend fun updateMemberProfile(teamId: UUID, userId: UUID, jerseyNumber: Int?, position: String?): TeamMember
}
