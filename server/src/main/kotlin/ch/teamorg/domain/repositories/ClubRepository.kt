package ch.teamorg.domain.repositories

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.ClubUser
import ch.teamorg.domain.models.Team
import java.util.UUID

interface ClubRepository {
    suspend fun create(name: String, sportType: String, location: String?, creatorUserId: UUID): Club
    suspend fun findById(id: UUID): Club?
    suspend fun update(id: UUID, name: String?, location: String?, logoUrl: String?): Club
    suspend fun listTeams(clubId: UUID): List<Team>
    suspend fun listUsers(clubId: UUID, limit: Int, offset: Int): List<ClubUser>
    suspend fun hasRole(userId: UUID, clubId: UUID, role: String): Boolean
    suspend fun isMember(userId: UUID, clubId: UUID): Boolean
}
