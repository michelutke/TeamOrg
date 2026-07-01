package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.count

class AttendanceRepositoryImpl : AttendanceRepository {

    override suspend fun getEventAttendance(eventId: UUID): List<AttendanceResponseRow> = transaction {
        AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.eventId eq eventId }
            .map(::rowToResponse)
    }

    override suspend fun getMyResponse(eventId: UUID, userId: UUID): AttendanceResponseRow? = transaction {
        AttendanceResponsesTable.selectAll()
            .where {
                (AttendanceResponsesTable.eventId eq eventId) and
                (AttendanceResponsesTable.userId eq userId)
            }
            .map(::rowToResponse)
            .singleOrNull()
    }

    override suspend fun upsertResponse(
        eventId: UUID,
        userId: UUID,
        status: String,
        reason: String?
    ): AttendanceResponseRow = transaction {
        val now = Instant.now()
        AttendanceResponsesTable.upsert(
            keys = arrayOf(AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId)
        ) {
            it[AttendanceResponsesTable.eventId] = eventId
            it[AttendanceResponsesTable.userId] = userId
            it[AttendanceResponsesTable.status] = status
            it[AttendanceResponsesTable.reason] = reason
            it[AttendanceResponsesTable.respondedAt] = now
            it[AttendanceResponsesTable.updatedAt] = now
            it[AttendanceResponsesTable.manualOverride] = true
        }
        AttendanceResponsesTable.selectAll()
            .where {
                (AttendanceResponsesTable.eventId eq eventId) and
                (AttendanceResponsesTable.userId eq userId)
            }
            .map(::rowToResponse)
            .single()
    }

    override suspend fun isPastCutoff(eventId: UUID): Boolean = transaction {
        val row = EventsTable
            .select(EventsTable.responseDeadline, EventsTable.startAt)
            .where { EventsTable.id eq eventId }
            .singleOrNull() ?: return@transaction false
        val cutoff = row[EventsTable.responseDeadline] ?: row[EventsTable.startAt]
        !Instant.now().isBefore(cutoff)
    }

    override suspend fun setResponseByCoach(
        eventId: UUID,
        targetUserId: UUID,
        status: String,
        unexcused: Boolean,
        setBy: UUID
    ): AttendanceResponseRow = transaction {
        val now = Instant.now()
        // unexcused is only meaningful for declined; force false for any other status
        val effectiveUnexcused = status == "declined" && unexcused
        AttendanceResponsesTable.upsert(
            keys = arrayOf(AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId)
        ) {
            it[AttendanceResponsesTable.eventId] = eventId
            it[AttendanceResponsesTable.userId] = targetUserId
            it[AttendanceResponsesTable.status] = status
            it[AttendanceResponsesTable.unexcused] = effectiveUnexcused
            it[AttendanceResponsesTable.manualOverride] = true
            it[AttendanceResponsesTable.respondedAt] = now
            it[AttendanceResponsesTable.updatedAt] = now
        }
        AttendanceResponsesTable.selectAll()
            .where {
                (AttendanceResponsesTable.eventId eq eventId) and
                (AttendanceResponsesTable.userId eq targetUserId)
            }
            .map(::rowToResponse)
            .single()
    }

    override suspend fun getCheckIn(eventId: UUID): List<CheckInRow> = transaction {
        AttendanceRecordsTable.selectAll()
            .where { AttendanceRecordsTable.eventId eq eventId }
            .map(::rowToCheckIn)
    }

    override suspend fun getCheckInEntries(eventId: UUID): List<CheckInEntryResponse> = transaction {
        // Get all team members for this event via event_teams → team_roles → users
        // Use explicit join conditions because EventTeamsTable and TeamRolesTable share no direct FK
        val teamMemberRows = EventTeamsTable
            .innerJoin(TeamRolesTable, { EventTeamsTable.teamId }, { TeamRolesTable.teamId })
            .innerJoin(UsersTable, { TeamRolesTable.userId }, { UsersTable.id })
            .select(
                UsersTable.id,
                UsersTable.displayName,
                UsersTable.avatarUrl
            )
            .where { EventTeamsTable.eventId eq eventId }
            .distinctBy { it[UsersTable.id] }

        // Load all responses and records for this event (keyed by userId)
        val responsesByUser = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.eventId eq eventId }
            .associateBy { it[AttendanceResponsesTable.userId] }

        val recordsByUser = AttendanceRecordsTable.selectAll()
            .where { AttendanceRecordsTable.eventId eq eventId }
            .associateBy { it[AttendanceRecordsTable.userId] }

        teamMemberRows.map { userRow ->
            val userId = userRow[UsersTable.id]
            val respRow = responsesByUser[userId]
            val recRow = recordsByUser[userId]

            val responseDto = respRow?.let {
                AttendanceResponseDto(
                    eventId = it[AttendanceResponsesTable.eventId].toString(),
                    userId = it[AttendanceResponsesTable.userId].toString(),
                    status = it[AttendanceResponsesTable.status],
                    reason = it[AttendanceResponsesTable.reason],
                    abwesenheitRuleId = it[AttendanceResponsesTable.abwesenheitRuleId]?.toString(),
                    manualOverride = it[AttendanceResponsesTable.manualOverride],
                    respondedAt = it[AttendanceResponsesTable.respondedAt]?.toKotlinInstant(),
                    updatedAt = it[AttendanceResponsesTable.updatedAt].toKotlinInstant()
                )
            }

            val recordDto = recRow?.let {
                AttendanceRecordDto(
                    eventId = it[AttendanceRecordsTable.eventId].toString(),
                    userId = it[AttendanceRecordsTable.userId].toString(),
                    status = it[AttendanceRecordsTable.status].name,
                    note = it[AttendanceRecordsTable.note],
                    setBy = it[AttendanceRecordsTable.setBy].toString(),
                    setAt = it[AttendanceRecordsTable.setAt].toKotlinInstant(),
                    previousStatus = it[AttendanceRecordsTable.previousStatus]?.name,
                    previousSetBy = it[AttendanceRecordsTable.previousSetBy]?.toString()
                )
            }

            CheckInEntryResponse(
                userId = userId.toString(),
                userName = userRow[UsersTable.displayName],
                userAvatar = userRow[UsersTable.avatarUrl],
                response = responseDto,
                record = recordDto
            )
        }
    }

    override suspend fun upsertCheckIn(
        eventId: UUID,
        userId: UUID,
        status: String,
        note: String?,
        setBy: UUID
    ): CheckInRow = transaction {
        val existing = AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.eventId eq eventId) and
                (AttendanceRecordsTable.userId eq userId)
            }
            .map(::rowToCheckIn)
            .singleOrNull()

        val previousStatus = existing?.status
        val previousSetBy = existing?.setBy
        val now = Instant.now()

        AttendanceRecordsTable.upsert(
            keys = arrayOf(AttendanceRecordsTable.eventId, AttendanceRecordsTable.userId)
        ) {
            it[AttendanceRecordsTable.eventId] = eventId
            it[AttendanceRecordsTable.userId] = userId
            it[AttendanceRecordsTable.status] = RecordStatus.valueOf(status)
            it[AttendanceRecordsTable.note] = note
            it[AttendanceRecordsTable.setBy] = setBy
            it[AttendanceRecordsTable.setAt] = now
            it[AttendanceRecordsTable.previousStatus] = previousStatus?.let { s -> RecordStatus.valueOf(s) }
            it[AttendanceRecordsTable.previousSetBy] = previousSetBy
        }

        AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.eventId eq eventId) and
                (AttendanceRecordsTable.userId eq userId)
            }
            .map(::rowToCheckIn)
            .single()
    }

    override suspend fun getRawAttendance(
        userId: UUID,
        from: Instant?,
        to: Instant?,
        restrictToTeamIds: Set<UUID>?
    ): List<RawAttendanceRow> =
        transaction {
            buildRawQuery(userId = userId, teamId = null, from = from, to = to, restrictToTeamIds = restrictToTeamIds)
        }

    override suspend fun getTeamAttendance(teamId: UUID, from: Instant?, to: Instant?): List<RawAttendanceRow> =
        transaction {
            buildRawQuery(userId = null, teamId = teamId, from = from, to = to)
        }

    override suspend fun finalize(eventId: UUID, byUser: UUID): FinalizeResult = transaction {
        // Roster = all team_roles.userId across the event's teams (same as check-in roster).
        val rosterIds = EventTeamsTable
            .innerJoin(TeamRolesTable, { EventTeamsTable.teamId }, { TeamRolesTable.teamId })
            .select(TeamRolesTable.userId)
            .where { EventTeamsTable.eventId eq eventId }
            .map { it[TeamRolesTable.userId] }
            .toSet()

        // Read existing responses for roster members
        val responsesByUser = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.eventId eq eventId }
            .associate { it[AttendanceResponsesTable.userId] to it[AttendanceResponsesTable.status] }

        val unsureIds = rosterIds.filter { responsesByUser[it] == "unsure" }
        if (unsureIds.isNotEmpty()) return@transaction FinalizeResult.BlockedUnsure(unsureIds)

        val event = EventsTable
            .select(EventsTable.defaultResponse)
            .where { EventsTable.id eq eventId }
            .single()
        val defaultResponse = event[EventsTable.defaultResponse]

        val noResponseIds = rosterIds.filter { userId ->
            val status = responsesByUser[userId]
            status == null || status == "no-response"
        }

        val now = Instant.now()

        when (defaultResponse) {
            "accepted" -> {
                for (userId in noResponseIds) {
                    AttendanceResponsesTable.upsert(
                        keys = arrayOf(AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId)
                    ) {
                        it[AttendanceResponsesTable.eventId] = eventId
                        it[AttendanceResponsesTable.userId] = userId
                        it[AttendanceResponsesTable.status] = "confirmed"
                        it[AttendanceResponsesTable.manualOverride] = false
                        it[AttendanceResponsesTable.updatedAt] = now
                    }
                }
            }
            "declined" -> {
                for (userId in noResponseIds) {
                    AttendanceResponsesTable.upsert(
                        keys = arrayOf(AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId)
                    ) {
                        it[AttendanceResponsesTable.eventId] = eventId
                        it[AttendanceResponsesTable.userId] = userId
                        it[AttendanceResponsesTable.status] = "declined"
                        it[AttendanceResponsesTable.unexcused] = false
                        it[AttendanceResponsesTable.manualOverride] = false
                        it[AttendanceResponsesTable.updatedAt] = now
                    }
                }
            }
            else -> { // "none"
                if (noResponseIds.isNotEmpty()) return@transaction FinalizeResult.BlockedNoResponse(noResponseIds)
            }
        }

        EventsTable.update({ EventsTable.id eq eventId }) {
            it[checkInCompletedAt] = now
        }

        FinalizeResult.Ok
    }

    override suspend fun reopen(eventId: UUID): Unit = transaction {
        EventsTable.update({ EventsTable.id eq eventId }) {
            it[checkInCompletedAt] = null
        }
    }

    override suspend fun bulkInsertAutoDeclines(
        ruleId: UUID,
        userId: UUID,
        eventUserPairs: List<Pair<UUID, UUID>>
    ): Unit = transaction {
        for ((eventId, pairUserId) in eventUserPairs) {
            // Skip pairs where the user already has a manual_override response
            val hasManualOverride = AttendanceResponsesTable.selectAll()
                .where {
                    (AttendanceResponsesTable.eventId eq eventId) and
                    (AttendanceResponsesTable.userId eq pairUserId) and
                    (AttendanceResponsesTable.manualOverride eq true)
                }
                .count() > 0
            if (hasManualOverride) continue

            val now = Instant.now()
            AttendanceResponsesTable.upsert(
                keys = arrayOf(AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId)
            ) {
                it[AttendanceResponsesTable.eventId] = eventId
                it[AttendanceResponsesTable.userId] = pairUserId
                it[AttendanceResponsesTable.status] = "declined-auto"
                it[AttendanceResponsesTable.abwesenheitRuleId] = ruleId
                it[AttendanceResponsesTable.manualOverride] = false
                it[AttendanceResponsesTable.updatedAt] = now
            }
        }
    }

    override suspend fun resetResponsesForEvent(eventId: UUID): Int = transaction {
        AttendanceResponsesTable.update({
            (AttendanceResponsesTable.eventId eq eventId) and
            (AttendanceResponsesTable.status inList listOf("confirmed", "declined", "unsure"))
        }) {
            it[AttendanceResponsesTable.status] = "no-response"
            it[AttendanceResponsesTable.reason] = null
            it[AttendanceResponsesTable.manualOverride] = false
            it[AttendanceResponsesTable.respondedAt] = null
            it[AttendanceResponsesTable.updatedAt] = Instant.now()
        }
    }

    override suspend fun presentCounts(eventIds: List<UUID>): Map<UUID, Int> = transaction {
        if (eventIds.isEmpty()) return@transaction emptyMap()
        val cnt = AttendanceRecordsTable.eventId.count()
        AttendanceRecordsTable
            .select(AttendanceRecordsTable.eventId, cnt)
            .where { (AttendanceRecordsTable.eventId inList eventIds) and (AttendanceRecordsTable.status eq RecordStatus.present) }
            .groupBy(AttendanceRecordsTable.eventId)
            .associate { it[AttendanceRecordsTable.eventId] to it[cnt].toInt() }
    }

    // --- Private helpers ---

    private fun buildRawQuery(
        userId: UUID?,
        teamId: UUID?,
        from: Instant?,
        to: Instant?,
        restrictToTeamIds: Set<UUID>? = null
    ): List<RawAttendanceRow> {
        val query = if (teamId != null) {
            (EventsTable innerJoin EventTeamsTable)
                .leftJoin(AttendanceResponsesTable, { EventsTable.id }, { AttendanceResponsesTable.eventId })
                .leftJoin(AttendanceRecordsTable, { EventsTable.id }, { AttendanceRecordsTable.eventId })
                .select(
                    EventsTable.id,
                    AttendanceResponsesTable.userId,
                    AttendanceResponsesTable.status,
                    AttendanceRecordsTable.status,
                    EventsTable.startAt
                )
                .where { EventTeamsTable.teamId eq teamId }
        } else if (restrictToTeamIds != null) {
            // Scope the target's rows to events targeting only the authorized teams (the caller's
            // managed teams that the target also belongs to). Distinct guards against an event
            // matching multiple authorized teams.
            (EventsTable innerJoin EventTeamsTable)
                .leftJoin(AttendanceResponsesTable, { EventsTable.id }, { AttendanceResponsesTable.eventId })
                .leftJoin(AttendanceRecordsTable, { EventsTable.id }, { AttendanceRecordsTable.eventId })
                .select(
                    EventsTable.id,
                    AttendanceResponsesTable.userId,
                    AttendanceResponsesTable.status,
                    AttendanceRecordsTable.status,
                    EventsTable.startAt
                )
                .where {
                    (AttendanceResponsesTable.userId eq userId!!) and
                        (EventTeamsTable.teamId inList restrictToTeamIds)
                }
                .withDistinct()
        } else {
            EventsTable
                .leftJoin(AttendanceResponsesTable, { EventsTable.id }, { AttendanceResponsesTable.eventId })
                .leftJoin(AttendanceRecordsTable, { EventsTable.id }, { AttendanceRecordsTable.eventId })
                .select(
                    EventsTable.id,
                    AttendanceResponsesTable.userId,
                    AttendanceResponsesTable.status,
                    AttendanceRecordsTable.status,
                    EventsTable.startAt
                )
                .where { AttendanceResponsesTable.userId eq userId!! }
        }

        if (from != null) query.andWhere { EventsTable.startAt greaterEq from }
        if (to != null) query.andWhere { EventsTable.startAt lessEq to }

        return query.map { row ->
            RawAttendanceRow(
                eventId = row[EventsTable.id],
                userId = row.getOrNull(AttendanceResponsesTable.userId) ?: userId ?: UUID(0, 0),
                responseStatus = row.getOrNull(AttendanceResponsesTable.status),
                recordStatus = row.getOrNull(AttendanceRecordsTable.status)?.name,
                eventStartAt = row[EventsTable.startAt]
            )
        }
    }

    private fun rowToResponse(row: ResultRow): AttendanceResponseRow = AttendanceResponseRow(
        eventId = row[AttendanceResponsesTable.eventId],
        userId = row[AttendanceResponsesTable.userId],
        status = row[AttendanceResponsesTable.status],
        reason = row[AttendanceResponsesTable.reason],
        abwesenheitRuleId = row[AttendanceResponsesTable.abwesenheitRuleId],
        manualOverride = row[AttendanceResponsesTable.manualOverride],
        unexcused = row[AttendanceResponsesTable.unexcused],
        respondedAt = row[AttendanceResponsesTable.respondedAt],
        updatedAt = row[AttendanceResponsesTable.updatedAt]
    )

    private fun rowToCheckIn(row: ResultRow): CheckInRow = CheckInRow(
        eventId = row[AttendanceRecordsTable.eventId],
        userId = row[AttendanceRecordsTable.userId],
        status = row[AttendanceRecordsTable.status].name,
        note = row[AttendanceRecordsTable.note],
        setBy = row[AttendanceRecordsTable.setBy],
        setAt = row[AttendanceRecordsTable.setAt],
        previousStatus = row[AttendanceRecordsTable.previousStatus]?.name,
        previousSetBy = row[AttendanceRecordsTable.previousSetBy]
    )
}
