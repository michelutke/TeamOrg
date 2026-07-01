package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.ParsedMember
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class TeamNdsInfo(
    val angebotId: String?,
    val kursName: String?,
    val hauptsportart: String?,
    val nutzergruppe: String?
)

/** One TeamOrg event flattened for NDS export (one Aktivitäten row). */
data class ExportActivity(
    val eventId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val location: String?,
    val ndsSymbol: String?,
    val eventType: String
)

/** One present attendance, joined to the member's NDS identity (one AWK row). */
data class ExportAttendance(
    val eventId: UUID,
    val personNumber: String?,
    val funktion: String,
    val lastName: String,
    val firstName: String
)

interface NdsRepository {
    suspend fun findTeamIdByAngebot(angebotId: String): UUID?
    suspend fun linkTeam(teamId: UUID, angebotId: String, kursName: String?, hauptsportart: String?, nutzergruppe: String?)
    suspend fun getTeamNds(teamId: UUID): TeamNdsInfo?
    /** Upsert all parsed members for a team, creating a provisional user + team role for new ones. */
    suspend fun importRoster(teamId: UUID, members: List<ParsedMember>): List<NdsMember>
    /** Upsert members from a dedicated person export (carries PERSONENNUMMER); merges by name. */
    suspend fun upsertMembers(teamId: UUID, members: List<NdsMemberInput>): List<NdsMember>
    suspend fun listMembers(teamId: UUID): List<NdsMember>
    suspend fun getMember(memberId: UUID): NdsMember?
    suspend fun updateMember(
        memberId: UUID,
        personNumber: String?,
        lastName: String?,
        firstName: String?,
        birthDate: java.time.LocalDate?
    ): NdsMember?
    /** Provisional user id backing a member (for invite + attendance import), or null. */
    suspend fun getMemberUserId(memberId: UUID): UUID?
    /** Link a claimed member to a real account: move attendance + role off the provisional user. */
    suspend fun claimMember(memberId: UUID, realUserId: UUID)

    /** Active events for the team (export source for the Aktivitäten file). */
    suspend fun listExportActivities(teamId: UUID): List<ExportActivity>
    /** Present attendance records joined to the team's NDS members (export source for AWK). */
    suspend fun listExportAttendances(teamId: UUID): List<ExportAttendance>
}

class NdsRepositoryImpl : NdsRepository {

    override suspend fun findTeamIdByAngebot(angebotId: String): UUID? = transaction {
        TeamsTable.select(TeamsTable.id).where { TeamsTable.ndsAngebotId eq angebotId }
            .map { it[TeamsTable.id] }
            .singleOrNull()
    }

    override suspend fun linkTeam(
        teamId: UUID,
        angebotId: String,
        kursName: String?,
        hauptsportart: String?,
        nutzergruppe: String?
    ): Unit = transaction {
        TeamsTable.update({ TeamsTable.id eq teamId }) {
            it[ndsAngebotId] = angebotId
            it[ndsKursName] = kursName
            it[ndsHauptsportart] = hauptsportart
            if (nutzergruppe != null) it[ndsNutzergruppe] = nutzergruppe
            it[updatedAt] = Instant.now()
        }
    }

    override suspend fun getTeamNds(teamId: UUID): TeamNdsInfo? = transaction {
        TeamsTable.selectAll().where { TeamsTable.id eq teamId }
            .map {
                TeamNdsInfo(
                    angebotId = it[TeamsTable.ndsAngebotId],
                    kursName = it[TeamsTable.ndsKursName],
                    hauptsportart = it[TeamsTable.ndsHauptsportart],
                    nutzergruppe = it[TeamsTable.ndsNutzergruppe]
                )
            }
            .singleOrNull()
    }

    override suspend fun importRoster(teamId: UUID, members: List<ParsedMember>): List<NdsMember> =
        upsertMembers(
            teamId,
            members.map { NdsMemberInput(it.lastName, it.firstName, it.birthDate, null, it.funktion) }
        )

    override suspend fun upsertMembers(teamId: UUID, members: List<NdsMemberInput>): List<NdsMember> = transaction {
        members.map { m ->
            val memberId = upsertOne(teamId, m)
            ensureUserAndRole(memberId, teamId, m.funktion, m.firstName, m.lastName)
            NdsMembersTable.selectAll().where { NdsMembersTable.id eq memberId }.single().toNdsMember()
        }
    }

    /**
     * Match an incoming person to an existing roster row by NAME (birthdate is unreliable across the
     * three NDS exports — the Teilnehmende CSV omits it). Merge rule: fill birthdate/person_number
     * only from non-null incoming values, never clobber an existing person_number with null.
     * Returns the member id.
     */
    private fun upsertOne(teamId: UUID, m: NdsMemberInput): UUID {
        val nameMatches = NdsMembersTable.selectAll().where {
            (NdsMembersTable.teamId eq teamId) and
                (NdsMembersTable.lastName eq m.lastName) and
                (NdsMembersTable.firstName eq m.firstName)
        }.toList()

        val target = when {
            m.birthDate != null ->
                nameMatches.firstOrNull { it[NdsMembersTable.birthDate] == m.birthDate }
                    ?: nameMatches.firstOrNull { it[NdsMembersTable.birthDate] == null }
            else -> nameMatches.firstOrNull()
        }

        if (target == null) {
            val newId = UUID.randomUUID()
            NdsMembersTable.insert {
                it[id] = newId
                it[NdsMembersTable.teamId] = teamId
                it[lastName] = m.lastName
                it[firstName] = m.firstName
                it[birthDate] = m.birthDate
                it[personNumber] = m.personNumber
                it[funktion] = m.funktion
                it[sourceKind] = "nds_import"
            }
            return newId
        }

        val memberId = target[NdsMembersTable.id]
        NdsMembersTable.update({ NdsMembersTable.id eq memberId }) {
            if (m.birthDate != null) it[birthDate] = m.birthDate
            if (m.personNumber != null) it[personNumber] = m.personNumber
            it[funktion] = m.funktion
            it[updatedAt] = Instant.now()
        }
        return memberId
    }

    /** Inside a transaction: if the member has no user yet, create a provisional one + team role. */
    private fun ensureUserAndRole(memberId: UUID, teamId: UUID, funktion: String, firstName: String, lastName: String) {
        val current = NdsMembersTable.select(NdsMembersTable.userId)
            .where { NdsMembersTable.id eq memberId }
            .single()[NdsMembersTable.userId]
        if (current != null) return

        val userId = UUID.randomUUID()
        UsersTable.insert {
            it[id] = userId
            it[email] = "nds-$memberId@import.teamorg.local"
            it[passwordHash] = "!" // unusable hash → cannot log in
            it[displayName] = listOf(firstName, lastName).filter { p -> p.isNotBlank() }.joinToString(" ")
            it[provisional] = true
        }
        NdsMembersTable.update({ NdsMembersTable.id eq memberId }) {
            it[NdsMembersTable.userId] = userId
        }
        val role = if (funktion == "Leiter/in") "coach" else "player"
        TeamRolesTable.insertIgnore {
            it[TeamRolesTable.userId] = userId
            it[TeamRolesTable.teamId] = teamId
            it[TeamRolesTable.role] = role
        }
    }

    override suspend fun listMembers(teamId: UUID): List<NdsMember> = transaction {
        NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq teamId }
            .orderBy(NdsMembersTable.funktion to SortOrder.ASC, NdsMembersTable.lastName to SortOrder.ASC)
            .map { it.toNdsMember() }
    }

    override suspend fun getMember(memberId: UUID): NdsMember? = transaction {
        NdsMembersTable.selectAll().where { NdsMembersTable.id eq memberId }
            .map { it.toNdsMember() }
            .singleOrNull()
    }

    override suspend fun updateMember(
        memberId: UUID,
        personNumber: String?,
        lastName: String?,
        firstName: String?,
        birthDate: java.time.LocalDate?
    ): NdsMember? = transaction {
        val updated = NdsMembersTable.update({ NdsMembersTable.id eq memberId }) {
            if (personNumber != null) it[NdsMembersTable.personNumber] = personNumber
            if (lastName != null) it[NdsMembersTable.lastName] = lastName
            if (firstName != null) it[NdsMembersTable.firstName] = firstName
            if (birthDate != null) it[NdsMembersTable.birthDate] = birthDate
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null
        else NdsMembersTable.selectAll().where { NdsMembersTable.id eq memberId }.single().toNdsMember()
    }

    override suspend fun getMemberUserId(memberId: UUID): UUID? = transaction {
        NdsMembersTable.select(NdsMembersTable.userId).where { NdsMembersTable.id eq memberId }
            .map { it[NdsMembersTable.userId] }
            .singleOrNull()
    }

    override suspend fun claimMember(memberId: UUID, realUserId: UUID): Unit = transaction {
        val row = NdsMembersTable.selectAll().where { NdsMembersTable.id eq memberId }.singleOrNull()
            ?: return@transaction
        val teamId = row[NdsMembersTable.teamId]
        val provisionalUserId = row[NdsMembersTable.userId]

        if (provisionalUserId == null) {
            NdsMembersTable.update({ NdsMembersTable.id eq memberId }) { it[userId] = realUserId }
            return@transaction
        }
        if (provisionalUserId == realUserId) return@transaction

        // Move attendance from the provisional placeholder to the real user, skipping events where
        // the real user already has a row (avoids PK clash on (event_id, user_id)).
        moveAttendance(AttendanceResponsesTable, AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId, provisionalUserId, realUserId)

        // Drop the provisional user's team role (the redeem already added the real user's role).
        TeamRolesTable.deleteWhere {
            Op.build { (TeamRolesTable.userId eq provisionalUserId) and (TeamRolesTable.teamId eq teamId) }
        }

        NdsMembersTable.update({ NdsMembersTable.id eq memberId }) { it[userId] = realUserId }

        // Remove the now-orphaned provisional placeholder account.
        val isProvisional = UsersTable.select(UsersTable.provisional)
            .where { UsersTable.id eq provisionalUserId }
            .map { it[UsersTable.provisional] }
            .singleOrNull() == true
        if (isProvisional) {
            UsersTable.deleteWhere { Op.build { UsersTable.id eq provisionalUserId } }
        }
    }

    private fun moveAttendance(
        table: Table,
        eventCol: Column<UUID>,
        userCol: Column<UUID>,
        from: UUID,
        to: UUID
    ) {
        val targetEvents = table.select(eventCol).where { userCol eq to }.map { it[eventCol] }.toSet()
        // Delete provisional rows that would collide with an existing real-user row, then repoint.
        if (targetEvents.isNotEmpty()) {
            table.deleteWhere { Op.build { (userCol eq from) and (eventCol inList targetEvents) } }
        }
        table.update({ userCol eq from }) { it[userCol] = to }
    }

    override suspend fun listExportActivities(teamId: UUID): List<ExportActivity> = transaction {
        (EventsTable innerJoin EventTeamsTable).selectAll()
            .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.status eq EventStatus.active) }
            .map {
                ExportActivity(
                    eventId = it[EventsTable.id],
                    startAt = it[EventsTable.startAt],
                    endAt = it[EventsTable.endAt],
                    location = it[EventsTable.location],
                    ndsSymbol = it[EventsTable.ndsSymbol],
                    eventType = it[EventsTable.type].name
                )
            }
    }

    override suspend fun listExportAttendances(teamId: UUID): List<ExportAttendance> = transaction {
        AttendanceResponsesTable
            .innerJoin(EventTeamsTable, { AttendanceResponsesTable.eventId }, { EventTeamsTable.eventId })
            .innerJoin(NdsMembersTable, { AttendanceResponsesTable.userId }, { NdsMembersTable.userId })
            .selectAll()
            .where {
                (EventTeamsTable.teamId eq teamId) and
                    (NdsMembersTable.teamId eq teamId) and
                    (AttendanceResponsesTable.status eq "confirmed")
            }
            .map {
                ExportAttendance(
                    eventId = it[AttendanceResponsesTable.eventId],
                    personNumber = it[NdsMembersTable.personNumber],
                    funktion = it[NdsMembersTable.funktion],
                    lastName = it[NdsMembersTable.lastName],
                    firstName = it[NdsMembersTable.firstName]
                )
            }
    }

    private fun ResultRow.toNdsMember() = NdsMember(
        id = this[NdsMembersTable.id],
        teamId = this[NdsMembersTable.teamId],
        userId = this[NdsMembersTable.userId],
        lastName = this[NdsMembersTable.lastName],
        firstName = this[NdsMembersTable.firstName],
        birthDate = this[NdsMembersTable.birthDate],
        personNumber = this[NdsMembersTable.personNumber],
        funktion = this[NdsMembersTable.funktion],
        source = this[NdsMembersTable.sourceKind],
        claimed = this[NdsMembersTable.userId] != null &&
            // claimed = backed by a non-provisional user
            !isProvisionalUser(this[NdsMembersTable.userId])
    )

    private fun isProvisionalUser(userId: UUID?): Boolean {
        if (userId == null) return false
        return UsersTable.select(UsersTable.provisional).where { UsersTable.id eq userId }
            .map { it[UsersTable.provisional] }
            .singleOrNull() == true
    }
}
