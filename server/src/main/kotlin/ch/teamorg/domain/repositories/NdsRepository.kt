package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.MovableCounts
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.ParsedMember
import ch.teamorg.infra.nds.MatchCandidateUser
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/** Thrown by [NdsMemberOps.applyMappingSync] when the target user is already linked to a
 *  DIFFERENT nds_members row of the same team (conflicting re-mapping). */
class NdsMappingConflictException(message: String) : Exception(message)

/**
 * Synchronous (non-suspend) member-row writes, callable directly inside an already-open Exposed
 * `transaction {}` block — used by [NdsEventImporter] so member application, event creation and
 * attendance all commit or roll back as ONE transaction. [NdsRepositoryImpl] delegates its suspend
 * methods to these for the single implementation.
 */
object NdsMemberOps {
    /**
     * Match an incoming person to an existing roster row by NAME (birthdate is unreliable across the
     * three NDS exports — the Teilnehmende CSV omits it). Merge rule: fill birthdate/person_number
     * only from non-null incoming values, never clobber an existing person_number with null.
     * Returns the member id.
     */
    fun upsertOneSync(teamId: UUID, m: NdsMemberInput): UUID {
        val target = findIdentityMatch(teamId, m)

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
    fun ensureUserAndRoleSync(memberId: UUID, teamId: UUID, funktion: String, firstName: String, lastName: String) {
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

    /**
     * Apply a `map` decision: upsert the nds_members row identified by [m] with `user_id = userId`,
     * OVERWRITING person_number/birth_date/funktion. Adds a team_roles row only if [userId] has none
     * for [teamId] yet. Never creates a provisional user. Throws [NdsMappingConflictException] if
     * [userId] is already linked to a DIFFERENT nds_members row of this team.
     */
    fun applyMappingSync(teamId: UUID, m: NdsMemberInput, userId: UUID) {
        val target = findIdentityMatch(teamId, m)
        val memberId = target?.get(NdsMembersTable.id) ?: run {
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
            newId
        }

        val conflicting = NdsMembersTable.selectAll()
            .where { (NdsMembersTable.teamId eq teamId) and (NdsMembersTable.userId eq userId) and (NdsMembersTable.id neq memberId) }
            .firstOrNull()
        if (conflicting != null) {
            throw NdsMappingConflictException(
                "Nutzer ist im Team bereits einem anderen Mitglied zugeordnet (${conflicting[NdsMembersTable.lastName]} ${conflicting[NdsMembersTable.firstName]})"
            )
        }

        NdsMembersTable.update({ NdsMembersTable.id eq memberId }) {
            it[NdsMembersTable.userId] = userId
            it[personNumber] = m.personNumber
            it[birthDate] = m.birthDate
            it[funktion] = m.funktion
            it[updatedAt] = Instant.now()
        }

        val hasRole = TeamRolesTable.selectAll()
            .where { (TeamRolesTable.userId eq userId) and (TeamRolesTable.teamId eq teamId) }
            .any()
        if (!hasRole) {
            TeamRolesTable.insert {
                it[TeamRolesTable.userId] = userId
                it[TeamRolesTable.teamId] = teamId
                it[TeamRolesTable.role] = roleForFunktion(m.funktion)
            }
        }
    }

    private fun roleForFunktion(funktion: String): String =
        if (funktion.contains("leiter", ignoreCase = true)) "coach" else "player"

    private fun findIdentityMatch(teamId: UUID, m: NdsMemberInput): ResultRow? {
        val nameMatches = NdsMembersTable.selectAll().where {
            (NdsMembersTable.teamId eq teamId) and
                (NdsMembersTable.lastName eq m.lastName) and
                (NdsMembersTable.firstName eq m.firstName)
        }.toList()

        return when {
            m.birthDate != null ->
                nameMatches.firstOrNull { it[NdsMembersTable.birthDate] == m.birthDate }
                    ?: nameMatches.firstOrNull { it[NdsMembersTable.birthDate] == null }
            else -> nameMatches.firstOrNull()
        }
    }
}

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
    /** The team (if any) in [clubId] already linked to this Angebot. Scoped per-club so importing
     *  an Angebot into one club never blocks importing it into another. */
    suspend fun findTeamIdByAngebot(angebotId: String, clubId: UUID): UUID?
    suspend fun linkTeam(teamId: UUID, angebotId: String, kursName: String?, hauptsportart: String?, nutzergruppe: String?)
    suspend fun getTeamNds(teamId: UUID): TeamNdsInfo?
    /** Upsert all parsed members for a team, creating a provisional user + team role for new ones. */
    suspend fun importRoster(teamId: UUID, members: List<ParsedMember>): List<NdsMember>
    /** Upsert members from a dedicated person export (carries PERSONENNUMMER); merges by name. */
    suspend fun upsertMembers(teamId: UUID, members: List<NdsMemberInput>): List<NdsMember>
    suspend fun listMembers(teamId: UUID): List<NdsMember>
    /**
     * Team roster joined with any already-linked nds_members identity, for match suggestions.
     * [excludeProvisional] defaults to false because the import wizard relies on a placeholder's
     * own linked identity matching itself to produce `alreadyLinkedUserId` and suppress a lossy
     * "map" onto the real account (see [ch.teamorg.infra.nds.NdsMemberMatcher.suggest]). The
     * duplicate-suggestions endpoint passes true: placeholders must never be offered as merge
     * candidates.
     */
    suspend fun listTeamUsersForMatching(teamId: UUID, excludeProvisional: Boolean = false): List<MatchCandidateUser>
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
    /** The roster row of [teamId] currently backed by [userId], or null if none. */
    suspend fun findMemberIdByUser(teamId: UUID, userId: UUID): UUID?
    /** True when [userId] is an import placeholder account (users.provisional). */
    suspend fun isProvisionalUser(userId: UUID): Boolean
    /** Roster rows of [teamId] not backed by a real account (no user, or a provisional one). */
    suspend fun listUnresolvedMembers(teamId: UUID): List<NdsMember>
    /** How many rows a merge would carry off [userId] within [teamId]. */
    suspend fun countMovableRows(userId: UUID, teamId: UUID): MovableCounts

    /** Active events for the team (export source for the Aktivitäten file). */
    suspend fun listExportActivities(teamId: UUID): List<ExportActivity>
    /** Present attendance records joined to the team's NDS members (export source for AWK). */
    suspend fun listExportAttendances(teamId: UUID): List<ExportAttendance>
}

class NdsRepositoryImpl : NdsRepository {

    override suspend fun findTeamIdByAngebot(angebotId: String, clubId: UUID): UUID? = transaction {
        TeamsTable.select(TeamsTable.id)
            .where { (TeamsTable.ndsAngebotId eq angebotId) and (TeamsTable.clubId eq clubId) }
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
            val memberId = NdsMemberOps.upsertOneSync(teamId, m)
            NdsMemberOps.ensureUserAndRoleSync(memberId, teamId, m.funktion, m.firstName, m.lastName)
            NdsMembersTable.selectAll().where { NdsMembersTable.id eq memberId }.single().toNdsMember()
        }
    }

    override suspend fun listMembers(teamId: UUID): List<NdsMember> = transaction {
        NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq teamId }
            .orderBy(NdsMembersTable.funktion to SortOrder.ASC, NdsMembersTable.lastName to SortOrder.ASC)
            .map { it.toNdsMember() }
    }

    override suspend fun listTeamUsersForMatching(teamId: UUID, excludeProvisional: Boolean): List<MatchCandidateUser> = transaction {
        val ndsByUser = NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq teamId }
            .filter { it[NdsMembersTable.userId] != null }
            .associateBy { it[NdsMembersTable.userId]!! }

        val userIds = TeamRolesTable.select(TeamRolesTable.userId)
            .where { TeamRolesTable.teamId eq teamId }
            .mapNotNull { it[TeamRolesTable.userId] }
            .distinct()
        if (userIds.isEmpty()) return@transaction emptyList()

        UsersTable.selectAll().where {
            if (excludeProvisional) {
                (UsersTable.id inList userIds) and (UsersTable.provisional eq false)
            } else {
                UsersTable.id inList userIds
            }
        }
            .map { user ->
                val userId = user[UsersTable.id]
                val displayName = user[UsersTable.displayName]
                val linked = ndsByUser[userId]
                MatchCandidateUser(
                    userId = userId,
                    firstName = displayName.substringBefore(" "),
                    lastName = displayName.substringAfter(" ", ""),
                    birthDate = linked?.get(NdsMembersTable.birthDate),
                    linkedNdsIdentity = linked?.let {
                        Triple(it[NdsMembersTable.lastName], it[NdsMembersTable.firstName], it[NdsMembersTable.birthDate])
                    }
                )
            }
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
        moveRows(AttendanceResponsesTable, AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId, provisionalUserId, realUserId)

        // Subgroup memberships and absence rules would otherwise be CASCADE-deleted with the
        // placeholder. Rules must move BEFORE the delete, or attendance_responses.abwesenheit_rule_id
        // on the rows just moved silently goes NULL.
        moveSubGroupMemberships(teamId, provisionalUserId, realUserId)
        AbwesenheitRulesTable.update({ AbwesenheitRulesTable.userId eq provisionalUserId }) {
            it[userId] = realUserId
        }

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

    /**
     * Repoint [table] rows from [from] to [to]. Rows whose [keyCol] the target already holds are
     * deleted instead of updated, so the move cannot clash on a (key, user) primary key.
     */
    private fun moveRows(
        table: Table,
        keyCol: Column<UUID>,
        userCol: Column<UUID>,
        from: UUID,
        to: UUID
    ) {
        val targetKeys = table.select(keyCol).where { userCol eq to }.map { it[keyCol] }.toSet()
        // Delete source rows that would collide with an existing target row, then repoint the rest.
        if (targetKeys.isNotEmpty()) {
            table.deleteWhere { Op.build { (userCol eq from) and (keyCol inList targetKeys) } }
        }
        table.update({ userCol eq from }) { it[userCol] = to }
    }

    /** Move the placeholder's memberships in THIS team's subgroups to the real account. */
    private fun moveSubGroupMemberships(teamId: UUID, from: UUID, to: UUID) {
        val teamSubGroupIds = SubGroupsTable.select(SubGroupsTable.id)
            .where { SubGroupsTable.teamId eq teamId }
            .map { it[SubGroupsTable.id] }
        if (teamSubGroupIds.isEmpty()) return

        val alreadyHeld = SubGroupMembersTable.select(SubGroupMembersTable.subGroupId)
            .where {
                (SubGroupMembersTable.userId eq to) and
                    (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
            }
            .map { it[SubGroupMembersTable.subGroupId] }

        if (alreadyHeld.isNotEmpty()) {
            SubGroupMembersTable.deleteWhere {
                Op.build { (userId eq from) and (subGroupId inList alreadyHeld) }
            }
        }
        SubGroupMembersTable.update({
            (SubGroupMembersTable.userId eq from) and
                (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
        }) {
            it[userId] = to
        }
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

    override suspend fun findMemberIdByUser(teamId: UUID, userId: UUID): UUID? = transaction {
        NdsMembersTable.select(NdsMembersTable.id)
            .where { (NdsMembersTable.teamId eq teamId) and (NdsMembersTable.userId eq userId) }
            .map { it[NdsMembersTable.id] }
            .firstOrNull()
    }

    override suspend fun isProvisionalUser(userId: UUID): Boolean = transaction {
        UsersTable.select(UsersTable.provisional)
            .where { UsersTable.id eq userId }
            .map { it[UsersTable.provisional] }
            .singleOrNull() == true
    }

    override suspend fun listUnresolvedMembers(teamId: UUID): List<NdsMember> = transaction {
        val provisionalIds = UsersTable.select(UsersTable.id)
            .where { UsersTable.provisional eq true }
            .map { it[UsersTable.id] }
            .toSet()
        NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq teamId }
            .filter { row ->
                val uid = row[NdsMembersTable.userId]
                uid == null || uid in provisionalIds
            }
            .map { it.toNdsMember() }
    }

    override suspend fun countMovableRows(userId: UUID, teamId: UUID): MovableCounts = transaction {
        val attendance = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.userId eq userId }.count().toInt()
        val teamSubGroupIds = SubGroupsTable.select(SubGroupsTable.id)
            .where { SubGroupsTable.teamId eq teamId }
            .map { it[SubGroupsTable.id] }
        val subgroups = if (teamSubGroupIds.isEmpty()) 0 else SubGroupMembersTable.selectAll()
            .where {
                (SubGroupMembersTable.userId eq userId) and
                    (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
            }.count().toInt()
        val rules = AbwesenheitRulesTable.selectAll()
            .where { AbwesenheitRulesTable.userId eq userId }.count().toInt()
        MovableCounts(attendance = attendance, subgroups = subgroups, rules = rules)
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
            isProvisionalUserSync(this[NdsMembersTable.userId]!!).not()
    )

    private fun isProvisionalUserSync(userId: UUID): Boolean =
        UsersTable.select(UsersTable.provisional).where { UsersTable.id eq userId }
            .map { it[UsersTable.provisional] }
            .singleOrNull() == true
}
