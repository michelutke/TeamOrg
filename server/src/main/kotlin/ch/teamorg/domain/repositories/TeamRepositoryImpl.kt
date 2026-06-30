package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.NotificationSettingsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamAppearance
import ch.teamorg.domain.models.TeamMember
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class TeamRepositoryImpl : TeamRepository {
    override suspend fun create(clubId: UUID, name: String, description: String?): Team = transaction {
        val teamId = TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[TeamsTable.name] = name
            it[TeamsTable.description] = description
        } get TeamsTable.id

        TeamsTable.selectAll().where { TeamsTable.id eq teamId }
            .map { rowToTeam(it, countMembers(teamId)) }
            .single()
    }

    override suspend fun findById(id: UUID): Team? = transaction {
        TeamsTable.selectAll().where { TeamsTable.id eq id }
            .map { rowToTeam(it, countMembers(id)) }
            .singleOrNull()
    }

    override suspend fun update(
        id: UUID,
        name: String?,
        description: String?,
        appearanceShape: String?,
        appearanceColor: String?
    ): Team = transaction {
        TeamsTable.update({ TeamsTable.id eq id }) {
            if (name != null) it[TeamsTable.name] = name
            if (description != null) it[TeamsTable.description] = description
            if (appearanceShape != null) it[TeamsTable.appearanceShape] = appearanceShape
            if (appearanceColor != null) it[TeamsTable.appearanceColor] = appearanceColor
            it[TeamsTable.updatedAt] = java.time.Instant.now()
        }

        TeamsTable.selectAll().where { TeamsTable.id eq id }
            .map { rowToTeam(it, countMembers(id)) }
            .single()
    }

    override suspend fun archive(id: UUID): Team = transaction {
        TeamsTable.update({ TeamsTable.id eq id }) {
            it[TeamsTable.archivedAt] = java.time.Instant.now()
            it[TeamsTable.updatedAt] = java.time.Instant.now()
        }

        TeamsTable.selectAll().where { TeamsTable.id eq id }
            .map { rowToTeam(it, countMembers(id)) }
            .single()
    }

    override suspend fun migrateTeam(sourceTeamId: UUID, targetTeamId: UUID): Int = transaction {
        // 1. Copy team_roles source -> target. insertIgnore => ON CONFLICT (user_id, team_id, role)
        //    DO NOTHING, so jersey_number/position are preserved only on newly-inserted rows.
        val sourceRoles = TeamRolesTable.selectAll().where { TeamRolesTable.teamId eq sourceTeamId }.toList()
        var movedMembers = 0
        for (r in sourceRoles) {
            val inserted = TeamRolesTable.insertIgnore {
                it[userId] = r[TeamRolesTable.userId]
                it[teamId] = targetTeamId
                it[role] = r[TeamRolesTable.role]
                it[jerseyNumber] = r[TeamRolesTable.jerseyNumber]
                it[position] = r[TeamRolesTable.position]
            }.insertedCount
            if (inserted > 0) movedMembers++
        }

        // 2. Carry games_sync_enabled to the target only when the target is still default (false).
        val sourceGamesSync = TeamsTable.select(TeamsTable.gamesSyncEnabled)
            .where { TeamsTable.id eq sourceTeamId }
            .map { it[TeamsTable.gamesSyncEnabled] }
            .single()
        val targetGamesSync = TeamsTable.select(TeamsTable.gamesSyncEnabled)
            .where { TeamsTable.id eq targetTeamId }
            .map { it[TeamsTable.gamesSyncEnabled] }
            .single()
        if (sourceGamesSync && !targetGamesSync) {
            TeamsTable.update({ TeamsTable.id eq targetTeamId }) {
                it[TeamsTable.gamesSyncEnabled] = true
                it[TeamsTable.updatedAt] = java.time.Instant.now()
            }
        }

        // 3. Copy each member's notification_settings source -> target where no target row exists.
        //    insertIgnore => ON CONFLICT (user_id, team_id) DO NOTHING.
        val sourceSettings = NotificationSettingsTable.selectAll()
            .where { NotificationSettingsTable.teamId eq sourceTeamId }
            .toList()
        for (s in sourceSettings) {
            NotificationSettingsTable.insertIgnore {
                it[userId] = s[NotificationSettingsTable.userId]
                it[teamId] = targetTeamId
                it[eventsNew] = s[NotificationSettingsTable.eventsNew]
                it[eventsEdit] = s[NotificationSettingsTable.eventsEdit]
                it[eventsCancel] = s[NotificationSettingsTable.eventsCancel]
                it[remindersEnabled] = s[NotificationSettingsTable.remindersEnabled]
                it[reminderLeadMinutes] = s[NotificationSettingsTable.reminderLeadMinutes]
                it[coachResponseMode] = s[NotificationSettingsTable.coachResponseMode]
                it[absencesEnabled] = s[NotificationSettingsTable.absencesEnabled]
                it[svGames] = s[NotificationSettingsTable.svGames]
            }
        }

        // 4. Lineage + archive the source.
        TeamsTable.update({ TeamsTable.id eq targetTeamId }) {
            it[TeamsTable.predecessorTeamId] = sourceTeamId
            it[TeamsTable.updatedAt] = java.time.Instant.now()
        }
        TeamsTable.update({ TeamsTable.id eq sourceTeamId }) {
            it[TeamsTable.archivedAt] = java.time.Instant.now()
            it[TeamsTable.updatedAt] = java.time.Instant.now()
        }

        movedMembers
    }

    override suspend fun listMembers(teamId: UUID): List<TeamMember> = transaction {
        (TeamRolesTable innerJoin UsersTable).selectAll().where { TeamRolesTable.teamId eq teamId }
            .map { row ->
                TeamMember(
                    userId = row[UsersTable.id].toString(),
                    displayName = row[UsersTable.displayName],
                    avatarUrl = row[UsersTable.avatarUrl],
                    role = row[TeamRolesTable.role],
                    jerseyNumber = row[TeamRolesTable.jerseyNumber],
                    position = row[TeamRolesTable.position]
                )
            }
    }

    override suspend fun hasRole(userId: UUID, teamId: UUID, vararg roles: String): Boolean = transaction {
        // 1. Check team roles
        val hasTeamRole = !TeamRolesTable.selectAll().where {
            (TeamRolesTable.userId eq userId) and
            (TeamRolesTable.teamId eq teamId) and
            (TeamRolesTable.role inList roles.toList())
        }.empty()

        if (hasTeamRole) return@transaction true

        // 2. Check if user is ClubManager of the club this team belongs to
        val clubId = TeamsTable.select(TeamsTable.clubId).where { TeamsTable.id eq teamId }
            .map { it[TeamsTable.clubId] }
            .singleOrNull()

        if (clubId != null) {
            return@transaction !ClubRolesTable.selectAll().where {
                (ClubRolesTable.userId eq userId) and
                (ClubRolesTable.clubId eq clubId) and
                (ClubRolesTable.role eq "club_manager")
            }.empty()
        }

        false
    }

    override suspend fun getClubId(teamId: UUID): UUID? = transaction {
        TeamsTable.select(TeamsTable.clubId).where { TeamsTable.id eq teamId }
            .map { it[TeamsTable.clubId] }
            .singleOrNull()
    }

    override suspend fun setGamesSyncEnabled(teamId: UUID, enabled: Boolean): Unit = transaction {
        TeamsTable.update({ TeamsTable.id eq teamId }) {
            it[TeamsTable.gamesSyncEnabled] = enabled
            it[TeamsTable.updatedAt] = java.time.Instant.now()
        }
    }

    override suspend fun addMember(teamId: UUID, userId: UUID, role: String): TeamMember = transaction {
        // The unique index is on (userId, teamId, role) — a user may theoretically hold multiple
        // roles on one team. Strategy: if exactly one row exists, UPDATE its role (preserving
        // jerseyNumber/position); if none, INSERT. If multiple rows already exist for this
        // user/team, update the first and leave the others (edge case, no delete).
        val existing = TeamRolesTable.selectAll().where {
            (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId)
        }.toList()

        if (existing.isNotEmpty()) {
            val firstId = existing.first()[TeamRolesTable.id]
            TeamRolesTable.update({ TeamRolesTable.id eq firstId }) {
                it[TeamRolesTable.role] = role
            }
        } else {
            TeamRolesTable.insert {
                it[TeamRolesTable.teamId] = teamId
                it[TeamRolesTable.userId] = userId
                it[TeamRolesTable.role] = role
            }
        }
        memberRow(teamId, userId)
    }

    override suspend fun updateMemberRole(teamId: UUID, userId: UUID, newRole: String): TeamMember {
        require(newRole in listOf("coach", "player")) { "Invalid role: $newRole" }
        return transaction {
            TeamRolesTable.update({
                (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId)
            }) {
                it[TeamRolesTable.role] = newRole
            }
            (TeamRolesTable innerJoin UsersTable).selectAll().where {
                (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId)
            }.map { row ->
                TeamMember(
                    userId = row[UsersTable.id].toString(),
                    displayName = row[UsersTable.displayName],
                    avatarUrl = row[UsersTable.avatarUrl],
                    role = row[TeamRolesTable.role],
                    jerseyNumber = row[TeamRolesTable.jerseyNumber],
                    position = row[TeamRolesTable.position]
                )
            }.single()
        }
    }

    override suspend fun removeMember(teamId: UUID, userId: UUID) {
        transaction {
            TeamRolesTable.deleteWhere {
                Op.build {
                    (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId)
                }
            }
        }
    }

    override suspend fun getUserClubRoles(userId: UUID): List<Pair<UUID, String>> = transaction {
        ClubRolesTable.selectAll().where { ClubRolesTable.userId eq userId }
            .map { Pair(it[ClubRolesTable.clubId], it[ClubRolesTable.role]) }
    }

    override suspend fun getUserTeamRoles(userId: UUID): List<Triple<UUID, UUID, String>> = transaction {
        (TeamRolesTable innerJoin TeamsTable).selectAll().where { TeamRolesTable.userId eq userId }
            .map { Triple(it[TeamRolesTable.teamId], it[TeamsTable.clubId], it[TeamRolesTable.role]) }
    }

    override suspend fun updateMemberProfile(teamId: UUID, userId: UUID, jerseyNumber: Int?, position: String?): TeamMember = transaction {
        TeamRolesTable.update({ (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId) }) {
            it[TeamRolesTable.jerseyNumber] = jerseyNumber
            it[TeamRolesTable.position] = position
        }
        (TeamRolesTable innerJoin UsersTable).selectAll()
            .where { (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId) }
            .map { row ->
                TeamMember(
                    userId = row[UsersTable.id].toString(),
                    displayName = row[UsersTable.displayName],
                    avatarUrl = row[UsersTable.avatarUrl],
                    role = row[TeamRolesTable.role],
                    jerseyNumber = row[TeamRolesTable.jerseyNumber],
                    position = row[TeamRolesTable.position]
                )
            }
            .single()
    }

    /** Fetch a single member row inside an existing transaction (called after an insert/update). */
    private fun memberRow(teamId: UUID, userId: UUID): TeamMember =
        (TeamRolesTable innerJoin UsersTable).selectAll().where {
            (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId)
        }.map { row ->
            TeamMember(
                userId = row[UsersTable.id].toString(),
                displayName = row[UsersTable.displayName],
                avatarUrl = row[UsersTable.avatarUrl],
                role = row[TeamRolesTable.role],
                jerseyNumber = row[TeamRolesTable.jerseyNumber],
                position = row[TeamRolesTable.position]
            )
        }.single()

    /** Count team members inside an existing transaction. */
    private fun countMembers(teamId: UUID): Int =
        TeamRolesTable.selectAll().where { TeamRolesTable.teamId eq teamId }.count().toInt()

    private fun rowToTeam(row: ResultRow, memberCount: Int = 0): Team {
        val shape = row[TeamsTable.appearanceShape]
        val color = row[TeamsTable.appearanceColor]
        val appearance = if (shape != null && color != null) TeamAppearance(shape, color) else null
        return Team(
            id = row[TeamsTable.id].toString(),
            clubId = row[TeamsTable.clubId].toString(),
            name = row[TeamsTable.name],
            memberCount = memberCount,
            description = row[TeamsTable.description],
            appearance = appearance,
            archivedAt = row[TeamsTable.archivedAt]?.toString(),
            createdAt = row[TeamsTable.createdAt].toString(),
            updatedAt = row[TeamsTable.updatedAt].toString()
        )
    }
}
