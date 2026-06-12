package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

interface AdminRepository {
    fun getDashboardStats(): DashboardStats
    fun searchUsers(query: String, page: Int = 1, pageSize: Int = 50): UserSearchPage
    fun findAllClubs(page: Int = 1, pageSize: Int = 50): ClubListPage
    fun findClubWithManagers(clubId: UUID): ClubDetail?
    fun createClub(name: String, sportType: String, location: String?): ClubListItem
    fun deactivateClub(clubId: UUID)
    fun reactivateClub(clubId: UUID)
    fun deleteClub(clubId: UUID)
    fun updateClub(clubId: UUID, name: String?, location: String?, sportType: String?)
    fun addClubManager(clubId: UUID, userId: UUID)
    fun removeClubManager(clubId: UUID, userId: UUID)
    fun getUserDetail(userId: UUID): UserDetail?
}

@Serializable
data class DashboardStats(
    val totalClubs: Long,
    val totalUsers: Long,
    val activeEventsToday: Long,
    val recentSignups: List<RecentSignup>
)

@Serializable
data class RecentSignup(
    val userId: String,
    val email: String,
    val displayName: String,
    val createdAt: String
)

@Serializable
data class UserSearchResult(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isSuperAdmin: Boolean,
    val createdAt: String,
    val clubs: List<UserClubMembership>
)

@Serializable
data class UserClubMembership(
    val clubId: String,
    val clubName: String,
    val role: String
)

@Serializable
data class UserSearchPage(
    val users: List<UserSearchResult>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long
)

@Serializable
data class ClubListItem(
    val id: String,
    val name: String,
    val sportType: String,
    val location: String?,
    val status: String,
    val teamCount: Long,
    val memberCount: Long,
    val createdAt: String
)

@Serializable
data class ClubListPage(
    val clubs: List<ClubListItem>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long
)

@Serializable
data class ClubDetail(
    val id: String,
    val name: String,
    val sportType: String,
    val location: String?,
    val status: String,
    val logoPath: String?,
    val managers: List<ClubManagerInfo>,
    val teamCount: Long,
    val memberCount: Long,
    val createdAt: String
)

@Serializable
data class ClubManagerInfo(
    val userId: String,
    val email: String,
    val displayName: String
)

@Serializable
data class UserDetail(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isSuperAdmin: Boolean,
    val createdAt: String,
    val clubMemberships: List<UserClubMembership>,
    val teamMemberships: List<UserTeamMembership>
)

@Serializable
data class UserTeamMembership(
    val teamId: String,
    val teamName: String,
    val clubId: String,
    val clubName: String,
    val role: String
)

class AdminRepositoryImpl : AdminRepository {
    override fun getDashboardStats(): DashboardStats = transaction {
        val totalClubs = ClubsTable.selectAll()
            .where { ClubsTable.status eq "active" }
            .count()
        val totalUsers = UsersTable.selectAll().count()
        val now = java.time.Instant.now()
        val activeEventsToday = EventsTable.selectAll()
            .where {
                (EventsTable.startAt lessEq now) and
                (EventsTable.endAt greaterEq now) and
                (EventsTable.status eq EventStatus.active)
            }
            .count()
        val recentSignups = UsersTable.selectAll()
            .orderBy(UsersTable.createdAt, SortOrder.DESC)
            .limit(10)
            .map { row ->
                RecentSignup(
                    userId = row[UsersTable.id].toString(),
                    email = row[UsersTable.email],
                    displayName = row[UsersTable.displayName],
                    createdAt = row[UsersTable.createdAt].toString()
                )
            }
        DashboardStats(totalClubs, totalUsers, activeEventsToday, recentSignups)
    }

    override fun searchUsers(query: String, page: Int, pageSize: Int): UserSearchPage = transaction {
        val lowerQuery = "%${query.lowercase()}%"
        val matchingUsers = UsersTable.selectAll()
            .where {
                (UsersTable.displayName.lowerCase() like lowerQuery) or
                (UsersTable.email.lowerCase() like lowerQuery)
            }
            .orderBy(UsersTable.createdAt, SortOrder.DESC)
            .toList()
        val totalCount = matchingUsers.size.toLong()
        val paged = matchingUsers.drop((page - 1) * pageSize).take(pageSize)
        val users = paged.map { userRow ->
            val userId = userRow[UsersTable.id]
            val clubs = ClubRolesTable
                .join(ClubsTable, JoinType.INNER, ClubRolesTable.clubId, ClubsTable.id)
                .selectAll()
                .where { ClubRolesTable.userId eq userId }
                .map { row ->
                    UserClubMembership(
                        clubId = row[ClubsTable.id].toString(),
                        clubName = row[ClubsTable.name],
                        role = row[ClubRolesTable.role]
                    )
                }
            UserSearchResult(
                id = userId.toString(),
                email = userRow[UsersTable.email],
                displayName = userRow[UsersTable.displayName],
                avatarUrl = userRow[UsersTable.avatarUrl],
                isSuperAdmin = userRow[UsersTable.isSuperAdmin],
                createdAt = userRow[UsersTable.createdAt].toString(),
                clubs = clubs
            )
        }
        UserSearchPage(users, page, pageSize, totalCount)
    }

    override fun findAllClubs(page: Int, pageSize: Int): ClubListPage = transaction {
        val totalCount = ClubsTable.selectAll().count()
        val clubs = ClubsTable.selectAll()
            .orderBy(ClubsTable.createdAt, SortOrder.DESC)
            .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
            .map { clubRow ->
                val clubId = clubRow[ClubsTable.id]
                val teamCount = TeamsTable.selectAll()
                    .where { TeamsTable.clubId eq clubId }
                    .count()
                val memberCount = TeamRolesTable
                    .join(TeamsTable, JoinType.INNER, TeamRolesTable.teamId, TeamsTable.id)
                    .select(TeamRolesTable.userId)
                    .where { (TeamsTable.clubId eq clubId) and TeamRolesTable.userId.isNotNull() }
                    .withDistinct()
                    .count()
                ClubListItem(
                    id = clubId.toString(),
                    name = clubRow[ClubsTable.name],
                    sportType = clubRow[ClubsTable.sportType],
                    location = clubRow[ClubsTable.location],
                    status = clubRow[ClubsTable.status],
                    teamCount = teamCount,
                    memberCount = memberCount,
                    createdAt = clubRow[ClubsTable.createdAt].toString()
                )
            }
        ClubListPage(clubs, page, pageSize, totalCount)
    }

    override fun findClubWithManagers(clubId: UUID): ClubDetail? = transaction {
        val clubRow = ClubsTable.selectAll().where { ClubsTable.id eq clubId }.singleOrNull()
            ?: return@transaction null
        val managers = ClubRolesTable
            .join(UsersTable, JoinType.INNER, ClubRolesTable.userId, UsersTable.id)
            .selectAll()
            .where { (ClubRolesTable.clubId eq clubId) and (ClubRolesTable.role eq "club_manager") }
            .map { row ->
                ClubManagerInfo(
                    userId = row[UsersTable.id].toString(),
                    email = row[UsersTable.email],
                    displayName = row[UsersTable.displayName]
                )
            }
        val teamCount = TeamsTable.selectAll().where { TeamsTable.clubId eq clubId }.count()
        val memberCount = TeamRolesTable
            .join(TeamsTable, JoinType.INNER, TeamRolesTable.teamId, TeamsTable.id)
            .select(TeamRolesTable.userId)
            .where { (TeamsTable.clubId eq clubId) and TeamRolesTable.userId.isNotNull() }
            .withDistinct()
            .count()
        ClubDetail(
            id = clubRow[ClubsTable.id].toString(),
            name = clubRow[ClubsTable.name],
            sportType = clubRow[ClubsTable.sportType],
            location = clubRow[ClubsTable.location],
            status = clubRow[ClubsTable.status],
            logoPath = clubRow[ClubsTable.logoPath],
            managers = managers,
            teamCount = teamCount,
            memberCount = memberCount,
            createdAt = clubRow[ClubsTable.createdAt].toString()
        )
    }

    override fun createClub(name: String, sportType: String, location: String?): ClubListItem = transaction {
        val newId = ClubsTable.insert {
            it[ClubsTable.name] = name
            it[ClubsTable.sportType] = sportType
            it[ClubsTable.location] = location
            it[ClubsTable.status] = "active"
        } get ClubsTable.id
        ClubListItem(
            id = newId.toString(),
            name = name,
            sportType = sportType,
            location = location,
            status = "active",
            teamCount = 0,
            memberCount = 0,
            createdAt = Instant.now().toString()
        )
    }

    override fun deactivateClub(clubId: UUID): Unit = transaction {
        ClubsTable.update({ ClubsTable.id eq clubId }) {
            it[ClubsTable.status] = "deactivated"
            it[ClubsTable.updatedAt] = Instant.now()
        }
    }

    override fun reactivateClub(clubId: UUID): Unit = transaction {
        ClubsTable.update({ ClubsTable.id eq clubId }) {
            it[ClubsTable.status] = "active"
            it[ClubsTable.updatedAt] = Instant.now()
        }
    }

    override fun deleteClub(clubId: UUID): Unit = transaction {
        ClubsTable.update({ ClubsTable.id eq clubId }) {
            it[ClubsTable.status] = "deleted"
            it[ClubsTable.updatedAt] = Instant.now()
        }
    }

    override fun updateClub(clubId: UUID, name: String?, location: String?, sportType: String?): Unit = transaction {
        ClubsTable.update({ ClubsTable.id eq clubId }) {
            if (name != null) it[ClubsTable.name] = name
            if (location != null) it[ClubsTable.location] = location
            if (sportType != null) it[ClubsTable.sportType] = sportType
            it[ClubsTable.updatedAt] = Instant.now()
        }
    }

    override fun addClubManager(clubId: UUID, userId: UUID): Unit = transaction {
        ClubRolesTable.insertIgnore {
            it[ClubRolesTable.userId] = userId
            it[ClubRolesTable.clubId] = clubId
            it[ClubRolesTable.role] = "club_manager"
        }
    }

    override fun removeClubManager(clubId: UUID, userId: UUID): Unit = transaction {
        ClubRolesTable.deleteWhere {
            (ClubRolesTable.userId eq userId) and
            (ClubRolesTable.clubId eq clubId) and
            (ClubRolesTable.role eq "club_manager")
        }
    }

    override fun getUserDetail(userId: UUID): UserDetail? = transaction {
        val userRow = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
            ?: return@transaction null
        val clubMemberships = ClubRolesTable
            .join(ClubsTable, JoinType.INNER, ClubRolesTable.clubId, ClubsTable.id)
            .selectAll()
            .where { ClubRolesTable.userId eq userId }
            .map { row ->
                UserClubMembership(
                    clubId = row[ClubsTable.id].toString(),
                    clubName = row[ClubsTable.name],
                    role = row[ClubRolesTable.role]
                )
            }
        val teamMemberships = TeamRolesTable
            .join(TeamsTable, JoinType.INNER, TeamRolesTable.teamId, TeamsTable.id)
            .join(ClubsTable, JoinType.INNER, TeamsTable.clubId, ClubsTable.id)
            .selectAll()
            .where { TeamRolesTable.userId eq userId }
            .map { row ->
                UserTeamMembership(
                    teamId = row[TeamsTable.id].toString(),
                    teamName = row[TeamsTable.name],
                    clubId = row[ClubsTable.id].toString(),
                    clubName = row[ClubsTable.name],
                    role = row[TeamRolesTable.role]
                )
            }
        UserDetail(
            id = userRow[UsersTable.id].toString(),
            email = userRow[UsersTable.email],
            displayName = userRow[UsersTable.displayName],
            avatarUrl = userRow[UsersTable.avatarUrl],
            isSuperAdmin = userRow[UsersTable.isSuperAdmin],
            createdAt = userRow[UsersTable.createdAt].toString(),
            clubMemberships = clubMemberships,
            teamMemberships = teamMemberships
        )
    }
}
