package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import ch.teamorg.domain.models.*
import ch.teamorg.domain.models.deriveCheckInStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// 0=Mon..6=Sun, matching the recurrence generator's weekday encoding.
private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

class EventRepositoryImpl : EventRepository {

    override suspend fun create(request: CreateEventRequest, createdBy: UUID): Event = transaction {
        if (request.recurring != null) {
            val series = createSeriesInternal(request, createdBy)
            // Create a seed event with team/subgroup associations so materialisation can copy them
            val seedEvent = insertSingleEvent(request, createdBy, seriesId = series.id, seriesSequence = 0)
            materialiseUpcomingOccurrencesInternal()
            seedEvent
        } else {
            insertSingleEvent(request, createdBy, seriesId = null, seriesSequence = null)
        }
    }

    private fun insertSingleEvent(
        request: CreateEventRequest,
        createdBy: UUID,
        seriesId: UUID?,
        seriesSequence: Int?
    ): Event {
        val eventId = EventsTable.insert {
            it[EventsTable.title] = request.title
            it[EventsTable.type] = EventType.valueOf(request.type)
            it[EventsTable.startAt] = request.startAt
            it[EventsTable.endAt] = request.endAt
            it[EventsTable.meetupAt] = request.meetupAt
            it[EventsTable.location] = request.location
            it[EventsTable.description] = request.description
            it[EventsTable.minAttendees] = request.minAttendees
            it[EventsTable.seriesId] = seriesId
            it[EventsTable.seriesSequence] = seriesSequence
            it[EventsTable.responseDeadline] = request.responseDeadline
            it[EventsTable.createdBy] = createdBy
        } get EventsTable.id

        for (teamId in request.teamIds) {
            EventTeamsTable.insert {
                it[EventTeamsTable.eventId] = eventId
                it[EventTeamsTable.teamId] = teamId
            }
        }
        for (subgroupId in request.subgroupIds) {
            EventSubgroupsTable.insert {
                it[EventSubgroupsTable.eventId] = eventId
                it[EventSubgroupsTable.subgroupId] = subgroupId
            }
        }

        return rowToEventWithRelations(eventId)
    }

    override suspend fun findById(id: UUID): Event? = transaction {
        EventsTable.selectAll().where { EventsTable.id eq id }
            .map(::rowToEvent)
            .singleOrNull()
            ?.let { event ->
                val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
                    .where { EventTeamsTable.eventId eq id }
                    .map { it[EventTeamsTable.teamId] }
                val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
                    .where { EventSubgroupsTable.eventId eq id }
                    .map { it[EventSubgroupsTable.subgroupId] }
                event.copy(teamIds = teamIds, subgroupIds = subgroupIds)
            }
    }

    override suspend fun findByIdWithTeams(id: UUID): EventWithTeams? = transaction {
        val event = EventsTable.selectAll().where { EventsTable.id eq id }
            .map(::rowToEvent)
            .singleOrNull() ?: return@transaction null

        val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
            .where { EventTeamsTable.eventId eq id }
            .map { it[EventTeamsTable.teamId] }

        val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
            .where { EventSubgroupsTable.eventId eq id }
            .map { it[EventSubgroupsTable.subgroupId] }

        val matchedTeams = (EventTeamsTable innerJoin TeamsTable)
            .select(TeamsTable.id, TeamsTable.name)
            .where { EventTeamsTable.eventId eq id }
            .map { MatchedTeam(id = it[TeamsTable.id], name = it[TeamsTable.name]) }

        val n = presentCountFor(listOf(id))[id] ?: 0
        EventWithTeams(
            event = event.copy(teamIds = teamIds, subgroupIds = subgroupIds, presentCount = n),
            matchedTeams = matchedTeams
        )
    }

    override suspend fun findEventsForUser(
        userId: UUID,
        from: Instant?,
        to: Instant?,
        type: String?,
        teamId: UUID?
    ): List<EventWithTeams> = transaction {
        // Find all team IDs for this user (direct team roles)
        val directTeamIds = TeamRolesTable.select(TeamRolesTable.teamId)
            .where { TeamRolesTable.userId eq userId }
            .map { it[TeamRolesTable.teamId] }

        // Also include teams from clubs where user is club_manager
        val managedClubIds = ClubRolesTable.select(ClubRolesTable.clubId)
            .where { (ClubRolesTable.userId eq userId) and (ClubRolesTable.role eq "club_manager") }
            .map { it[ClubRolesTable.clubId] }
        val clubTeamIds = if (managedClubIds.isNotEmpty()) {
            TeamsTable.select(TeamsTable.id)
                .where { (TeamsTable.clubId inList managedClubIds) and TeamsTable.archivedAt.isNull() }
                .map { it[TeamsTable.id] }
        } else emptyList()

        val userTeamIds = (directTeamIds + clubTeamIds).distinct()

        if (userTeamIds.isEmpty()) return@transaction emptyList()

        // Find events targeting those teams
        var query = EventsTable
            .innerJoin(EventTeamsTable, { EventsTable.id }, { EventTeamsTable.eventId })
            .selectAll()
            .where { EventTeamsTable.teamId inList userTeamIds }

        if (from != null) query = query.andWhere { EventsTable.startAt greaterEq from }
        if (to != null) query = query.andWhere { EventsTable.startAt lessEq to }
        if (type != null) query = query.andWhere { EventsTable.type eq EventType.valueOf(type) }
        if (teamId != null) query = query.andWhere { EventTeamsTable.teamId eq teamId }

        // Filter by subgroup audience: if event has subgroup restrictions, user must be in one of them
        val eventRows = query.map(::rowToEvent)
        val uniqueEventIds = eventRows.map { it.id }.distinct()

        // For events with subgroup restrictions, filter out events where user is not in any listed subgroup
        val userSubgroupIds = SubGroupMembersTable.select(SubGroupMembersTable.subGroupId)
            .where { SubGroupMembersTable.userId eq userId }
            .map { it[SubGroupMembersTable.subGroupId] }

        val eventsWithSubgroups = EventSubgroupsTable
            .select(EventSubgroupsTable.eventId, EventSubgroupsTable.subgroupId)
            .where { EventSubgroupsTable.eventId inList uniqueEventIds }
            .groupBy { it[EventSubgroupsTable.eventId] }

        val filteredEventIds = uniqueEventIds.filter { eid ->
            val subgroupRestrictions = eventsWithSubgroups[eid]
            if (subgroupRestrictions == null || subgroupRestrictions.isEmpty()) {
                true
            } else {
                subgroupRestrictions.any { row ->
                    row[EventSubgroupsTable.subgroupId] in userSubgroupIds
                }
            }
        }

        // Build EventWithTeams for each unique event
        val counts = presentCountFor(filteredEventIds)
        filteredEventIds.mapNotNull { eid ->
            val event = eventRows.firstOrNull { it.id == eid } ?: return@mapNotNull null
            val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
                .where { EventTeamsTable.eventId eq eid }
                .map { it[EventTeamsTable.teamId] }
            val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
                .where { EventSubgroupsTable.eventId eq eid }
                .map { it[EventSubgroupsTable.subgroupId] }
            val matchedTeams = (EventTeamsTable innerJoin TeamsTable)
                .select(TeamsTable.id, TeamsTable.name)
                .where {
                    (EventTeamsTable.eventId eq eid) and
                    (EventTeamsTable.teamId inList userTeamIds)
                }
                .map { MatchedTeam(id = it[TeamsTable.id], name = it[TeamsTable.name]) }
            EventWithTeams(
                event = event.copy(teamIds = teamIds, subgroupIds = subgroupIds, presentCount = counts[eid] ?: 0),
                matchedTeams = matchedTeams
            )
        }
    }

    override suspend fun findEventsForTeam(teamId: UUID, from: Instant?, to: Instant?): List<Event> = transaction {
        var query = (EventsTable innerJoin EventTeamsTable)
            .selectAll()
            .where { EventTeamsTable.teamId eq teamId }

        if (from != null) query = query.andWhere { EventsTable.startAt greaterEq from }
        if (to != null) query = query.andWhere { EventsTable.startAt lessEq to }

        query.map(::rowToEvent)
    }

    override suspend fun update(id: UUID, request: EditEventRequest): Event? = transaction {
        val updated = EventsTable.update({ EventsTable.id eq id }) { stmt ->
            if (request.title != null) stmt[EventsTable.title] = request.title
            if (request.type != null) stmt[EventsTable.type] = EventType.valueOf(request.type)
            if (request.startAt != null) stmt[EventsTable.startAt] = request.startAt
            if (request.endAt != null) stmt[EventsTable.endAt] = request.endAt
            stmt[EventsTable.meetupAt] = request.meetupAt ?: EventsTable.selectAll()
                .where { EventsTable.id eq id }
                .map { it[EventsTable.meetupAt] }
                .singleOrNull()
            if (request.location != null) stmt[EventsTable.location] = request.location
            if (request.description != null) stmt[EventsTable.description] = request.description
            if (request.minAttendees != null) stmt[EventsTable.minAttendees] = request.minAttendees
            stmt[EventsTable.updatedAt] = Instant.now()
        }
        if (updated == 0) return@transaction null

        if (request.teamIds != null) {
            EventTeamsTable.deleteWhere { EventTeamsTable.eventId eq id }
            for (teamId in request.teamIds) {
                EventTeamsTable.insert {
                    it[EventTeamsTable.eventId] = id
                    it[EventTeamsTable.teamId] = teamId
                }
            }
        }
        if (request.subgroupIds != null) {
            EventSubgroupsTable.deleteWhere { EventSubgroupsTable.eventId eq id }
            for (subgroupId in request.subgroupIds) {
                EventSubgroupsTable.insert {
                    it[EventSubgroupsTable.eventId] = id
                    it[EventSubgroupsTable.subgroupId] = subgroupId
                }
            }
        }

        rowToEventWithRelations(id)
    }

    override suspend fun cancel(id: UUID): Event? = transaction {
        val now = Instant.now()
        val updated = EventsTable.update({ EventsTable.id eq id }) {
            it[EventsTable.status] = EventStatus.cancelled
            it[EventsTable.cancelledAt] = now
            it[EventsTable.updatedAt] = now
        }
        if (updated == 0) null
        else rowToEventWithRelations(id)
    }

    override suspend fun uncancel(id: UUID): Event? = transaction {
        val updated = EventsTable.update({ EventsTable.id eq id }) {
            it[EventsTable.status] = EventStatus.active
            it[EventsTable.cancelledAt] = null
            it[EventsTable.updatedAt] = Instant.now()
        }
        if (updated == 0) null
        else rowToEventWithRelations(id)
    }

    override suspend fun uncancelFutureInSeries(seriesId: UUID, fromSequence: Int): Int = transaction {
        EventsTable.update({
            (EventsTable.seriesId eq seriesId) and
            (EventsTable.seriesSequence greaterEq fromSequence) and
            (EventsTable.status eq EventStatus.cancelled)
        }) {
            it[EventsTable.status] = EventStatus.active
            it[EventsTable.cancelledAt] = null
            it[EventsTable.updatedAt] = Instant.now()
        }
    }

    override suspend fun duplicate(id: UUID, createdBy: UUID): Event? = transaction {
        val source = EventsTable.selectAll().where { EventsTable.id eq id }
            .map(::rowToEvent)
            .singleOrNull() ?: return@transaction null

        val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
            .where { EventTeamsTable.eventId eq id }
            .map { it[EventTeamsTable.teamId] }
        val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
            .where { EventSubgroupsTable.eventId eq id }
            .map { it[EventSubgroupsTable.subgroupId] }

        insertSingleEvent(
            request = CreateEventRequest(
                title = source.title,
                type = source.type,
                startAt = source.startAt,
                endAt = source.endAt,
                meetupAt = source.meetupAt,
                location = source.location,
                description = source.description,
                minAttendees = source.minAttendees,
                teamIds = teamIds,
                subgroupIds = subgroupIds,
                recurring = null
            ),
            createdBy = createdBy,
            seriesId = null,
            seriesSequence = null
        )
    }

    override suspend fun createSeries(request: CreateEventRequest, createdBy: UUID): EventSeries = transaction {
        createSeriesInternal(request, createdBy)
    }

    private fun createSeriesInternal(request: CreateEventRequest, createdBy: UUID): EventSeries {
        val pattern = requireNotNull(request.recurring) { "recurring pattern required" }
        val seriesStartDate = request.startAt.atZone(ZoneOffset.UTC).toLocalDate()

        val seriesId = EventSeriesTable.insert {
            it[EventSeriesTable.patternType] = PatternType.valueOf(pattern.patternType)
            it[EventSeriesTable.weekdays] = pattern.weekdays
            it[EventSeriesTable.intervalDays] = pattern.intervalDays
            it[EventSeriesTable.seriesStartDate] = seriesStartDate
            it[EventSeriesTable.seriesEndDate] = pattern.seriesEndDate
            it[EventSeriesTable.templateStartTime] = request.startAt.atZone(ZoneOffset.UTC).toLocalTime()
            it[EventSeriesTable.templateEndTime] = request.endAt.atZone(ZoneOffset.UTC).toLocalTime()
            it[EventSeriesTable.templateMeetupTime] = request.meetupAt?.atZone(ZoneOffset.UTC)?.toLocalTime()
            it[EventSeriesTable.templateTitle] = request.title
            it[EventSeriesTable.templateType] = EventType.valueOf(request.type)
            it[EventSeriesTable.templateLocation] = request.location
            it[EventSeriesTable.templateDescription] = request.description
            it[EventSeriesTable.templateMinAttendees] = request.minAttendees
            it[EventSeriesTable.createdBy] = createdBy
        } get EventSeriesTable.id

        return EventSeriesTable.selectAll().where { EventSeriesTable.id eq seriesId }
            .map(::rowToEventSeries)
            .single()
    }

    override suspend fun findSeriesById(id: UUID): EventSeries? = transaction {
        EventSeriesTable.selectAll().where { EventSeriesTable.id eq id }
            .map(::rowToEventSeries)
            .singleOrNull()
    }

    override suspend fun updateSeriesTemplate(seriesId: UUID, request: EditEventRequest) = transaction {
        EventSeriesTable.update({ EventSeriesTable.id eq seriesId }) { stmt ->
            if (request.title != null) stmt[EventSeriesTable.templateTitle] = request.title
            if (request.type != null) stmt[EventSeriesTable.templateType] = EventType.valueOf(request.type)
            if (request.location != null) stmt[EventSeriesTable.templateLocation] = request.location
            if (request.description != null) stmt[EventSeriesTable.templateDescription] = request.description
            if (request.minAttendees != null) stmt[EventSeriesTable.templateMinAttendees] = request.minAttendees
        }
        Unit
    }

    override suspend fun materialiseUpcomingOccurrences(): Int = transaction {
        materialiseUpcomingOccurrencesInternal()
    }

    private fun materialiseUpcomingOccurrencesInternal(): Int {
        val today = LocalDate.now(ZoneOffset.UTC)
        val horizon = today.plusMonths(12)
        var created = 0

        val seriesList = EventSeriesTable.selectAll()
            .where { EventSeriesTable.seriesEndDate.isNull() or (EventSeriesTable.seriesEndDate greaterEq today) }
            .map(::rowToEventSeries)

        for (series in seriesList) {
            // Find the max sequence already materialised for this series
            val maxSeq = EventsTable.select(EventsTable.seriesSequence)
                .where { EventsTable.seriesId eq series.id }
                .mapNotNull { it[EventsTable.seriesSequence] }
                .maxOrNull() ?: -1

            // Generate occurrence dates
            val occurrenceDates = generateOccurrenceDates(series, today, horizon)

            // Skip already materialised by taking only those after maxSeq
            val toCreate = occurrenceDates.drop(maxSeq + 1)
            if (toCreate.isEmpty()) continue

            // Copy team/subgroup associations from the first event in the series
            val referenceEventId = EventsTable.select(EventsTable.id)
                .where { EventsTable.seriesId eq series.id }
                .orderBy(EventsTable.seriesSequence)
                .limit(1)
                .map { it[EventsTable.id] }
                .firstOrNull()
            val seriesTeamIds = if (referenceEventId != null) {
                EventTeamsTable.select(EventTeamsTable.teamId)
                    .where { EventTeamsTable.eventId eq referenceEventId }
                    .map { it[EventTeamsTable.teamId] }
            } else emptyList()
            val seriesSubgroupIds = if (referenceEventId != null) {
                EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
                    .where { EventSubgroupsTable.eventId eq referenceEventId }
                    .map { it[EventSubgroupsTable.subgroupId] }
            } else emptyList()

            for ((seq, date) in toCreate.withIndex()) {
                val actualSeq = maxSeq + 1 + seq
                val startInstant = date.atTime(series.templateStartTime).toInstant(ZoneOffset.UTC)
                val endInstant = date.atTime(series.templateEndTime).toInstant(ZoneOffset.UTC)
                val meetupInstant = series.templateMeetupTime?.let { date.atTime(it).toInstant(ZoneOffset.UTC) }

                val newEventId = EventsTable.insert {
                    it[EventsTable.title] = series.templateTitle
                    it[EventsTable.type] = EventType.valueOf(series.templateType)
                    it[EventsTable.startAt] = startInstant
                    it[EventsTable.endAt] = endInstant
                    it[EventsTable.meetupAt] = meetupInstant
                    it[EventsTable.location] = series.templateLocation
                    it[EventsTable.description] = series.templateDescription
                    it[EventsTable.minAttendees] = series.templateMinAttendees
                    it[EventsTable.seriesId] = series.id
                    it[EventsTable.seriesSequence] = actualSeq
                    it[EventsTable.createdBy] = series.createdBy
                } get EventsTable.id

                for (teamId in seriesTeamIds) {
                    EventTeamsTable.insert {
                        it[EventTeamsTable.eventId] = newEventId
                        it[EventTeamsTable.teamId] = teamId
                    }
                }
                for (subgroupId in seriesSubgroupIds) {
                    EventSubgroupsTable.insert {
                        it[EventSubgroupsTable.eventId] = newEventId
                        it[EventSubgroupsTable.subgroupId] = subgroupId
                    }
                }

                created++
            }
        }

        return created
    }

    private fun generateOccurrenceDates(series: EventSeries, from: LocalDate, horizon: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val endDate = series.seriesEndDate?.let { if (it < horizon) it else horizon } ?: horizon

        when (series.patternType) {
            "weekly" -> {
                val weekdays = series.weekdays ?: return emptyList()
                var current = series.seriesStartDate
                while (!current.isAfter(endDate)) {
                    // dayOfWeek: Monday=1..Sunday=7; weekdays array: 0=Mon..6=Sun
                    val dow = (current.dayOfWeek.value - 1).toShort()
                    if (dow in weekdays) {
                        dates.add(current)
                    }
                    current = current.plusDays(1)
                }
            }
            "daily" -> {
                var current = series.seriesStartDate
                while (!current.isAfter(endDate)) {
                    dates.add(current)
                    current = current.plusDays(1)
                }
            }
            "custom" -> {
                val interval = series.intervalDays ?: return emptyList()
                var current = series.seriesStartDate
                while (!current.isAfter(endDate)) {
                    dates.add(current)
                    current = current.plusDays(interval.toLong())
                }
            }
            else -> {}
        }
        return dates
    }

    override suspend fun cancelFutureInSeries(seriesId: UUID, fromSequence: Int): Int = transaction {
        val now = Instant.now()
        EventsTable.update({
            (EventsTable.seriesId eq seriesId) and
            (EventsTable.seriesSequence greaterEq fromSequence) and
            (EventsTable.seriesOverride eq false)
        }) {
            it[EventsTable.status] = EventStatus.cancelled
            it[EventsTable.cancelledAt] = now
            it[EventsTable.updatedAt] = now
        }
    }

    override suspend fun updateFutureInSeries(seriesId: UUID, fromSequence: Int, request: EditEventRequest): Int = transaction {
        val now = Instant.now()
        EventsTable.update({
            (EventsTable.seriesId eq seriesId) and
            (EventsTable.seriesSequence greaterEq fromSequence) and
            (EventsTable.seriesOverride eq false) and
            (EventsTable.startAt greater now)
        }) { stmt ->
            if (request.title != null) stmt[EventsTable.title] = request.title
            if (request.type != null) stmt[EventsTable.type] = EventType.valueOf(request.type)
            if (request.location != null) stmt[EventsTable.location] = request.location
            if (request.description != null) stmt[EventsTable.description] = request.description
            if (request.minAttendees != null) stmt[EventsTable.minAttendees] = request.minAttendees
            stmt[EventsTable.updatedAt] = now
        }
    }

    override suspend fun findByExternalGameId(source: String, gameId: Long): SyncedEventRef? = transaction {
        EventsTable.selectAll()
            .where { (EventsTable.externalSource eq source) and (EventsTable.externalGameId eq gameId) }
            .map(::rowToSyncedEventRef)
            .singleOrNull()
    }

    override suspend fun createSyncedMatch(
        title: String,
        startAt: Instant,
        endAt: Instant,
        location: String?,
        externalSource: String,
        externalGameId: Long,
        externalHash: String,
        createdBy: UUID,
        teamIds: List<UUID>
    ): Event = transaction {
        val now = Instant.now()
        val eventId = EventsTable.insert {
            it[EventsTable.title] = title
            it[EventsTable.type] = EventType.match
            it[EventsTable.startAt] = startAt
            it[EventsTable.endAt] = endAt
            it[EventsTable.location] = location
            it[EventsTable.status] = EventStatus.active
            it[EventsTable.externalSource] = externalSource
            it[EventsTable.externalGameId] = externalGameId
            it[EventsTable.externalHash] = externalHash
            it[EventsTable.externalSyncedAt] = now
            it[EventsTable.externalStatus] = "synced"
            it[EventsTable.needsReview] = false
            it[EventsTable.createdBy] = createdBy
        } get EventsTable.id

        for (teamId in teamIds) {
            EventTeamsTable.insert {
                it[EventTeamsTable.eventId] = eventId
                it[EventTeamsTable.teamId] = teamId
            }
        }

        rowToEventWithRelations(eventId)
    }

    override suspend fun updateSyncedFacts(
        eventId: UUID,
        title: String,
        startAt: Instant,
        endAt: Instant,
        location: String?,
        newHash: String
    ): Event? = transaction {
        val now = Instant.now()
        val updated = EventsTable.update({ EventsTable.id eq eventId }) {
            it[EventsTable.title] = title
            it[EventsTable.startAt] = startAt
            it[EventsTable.endAt] = endAt
            it[EventsTable.location] = location
            it[EventsTable.externalHash] = newHash
            it[EventsTable.externalSyncedAt] = now
            it[EventsTable.needsReview] = true
            it[EventsTable.updatedAt] = now
        }
        if (updated == 0) null
        else rowToEventWithRelations(eventId)
    }

    override suspend fun markPostponed(eventId: UUID): Event? = transaction {
        val updated = EventsTable.update({ EventsTable.id eq eventId }) {
            it[EventsTable.externalStatus] = "postponed"
            it[EventsTable.updatedAt] = Instant.now()
        }
        if (updated == 0) null
        else rowToEventWithRelations(eventId)
    }

    override suspend fun clearPostponedToSynced(eventId: UUID): Event? = transaction {
        val updated = EventsTable.update({ EventsTable.id eq eventId }) {
            it[EventsTable.externalStatus] = "synced"
            it[EventsTable.updatedAt] = Instant.now()
        }
        if (updated == 0) null
        else rowToEventWithRelations(eventId)
    }

    override suspend fun clearNeedsReview(eventId: UUID): Event? = transaction {
        val updated = EventsTable.update({ EventsTable.id eq eventId }) {
            it[EventsTable.needsReview] = false
            it[EventsTable.updatedAt] = Instant.now()
        }
        if (updated == 0) null
        else rowToEventWithRelations(eventId)
    }

    override suspend fun listImportableSeries(teamId: UUID): ImportableSeriesResult = transaction {
        val hasOwnSeries = (EventsTable innerJoin EventTeamsTable)
            .select(EventsTable.id)
            .where { (EventTeamsTable.teamId eq teamId) and EventsTable.seriesId.isNotNull() }
            .limit(1)
            .any()

        val predecessorTeamId = TeamsTable
            .select(TeamsTable.predecessorTeamId)
            .where { TeamsTable.id eq teamId }
            .firstOrNull()
            ?.get(TeamsTable.predecessorTeamId)

        val series = if (predecessorTeamId == null) {
            emptyList()
        } else {
            val seriesIds = (EventsTable innerJoin EventTeamsTable)
                .select(EventsTable.seriesId)
                .where { (EventTeamsTable.teamId eq predecessorTeamId) and EventsTable.seriesId.isNotNull() }
                .mapNotNull { it[EventsTable.seriesId] }
                .distinct()

            if (seriesIds.isEmpty()) {
                emptyList()
            } else {
                EventSeriesTable.selectAll()
                    .where { EventSeriesTable.id inList seriesIds }
                    .map(::rowToImportableSeries)
            }
        }

        ImportableSeriesResult(
            hasOwnSeries = hasOwnSeries,
            predecessorTeamId = predecessorTeamId?.toString(),
            series = series
        )
    }

    private fun rowToImportableSeries(row: ResultRow): ImportableSeries {
        val type = row[EventSeriesTable.templateType].name
        val weekdays = row[EventSeriesTable.weekdays]
        val patternType = row[EventSeriesTable.patternType].name
        return ImportableSeries(
            seriesId = row[EventSeriesTable.id],
            patternType = patternType,
            weekdays = weekdays,
            intervalDays = row[EventSeriesTable.intervalDays],
            templateStartTime = row[EventSeriesTable.templateStartTime],
            templateEndTime = row[EventSeriesTable.templateEndTime],
            templateMeetupTime = row[EventSeriesTable.templateMeetupTime],
            templateTitle = row[EventSeriesTable.templateTitle],
            templateType = type,
            templateLocation = row[EventSeriesTable.templateLocation],
            templateMinAttendees = row[EventSeriesTable.templateMinAttendees],
            seriesStartDate = row[EventSeriesTable.seriesStartDate],
            seriesEndDate = row[EventSeriesTable.seriesEndDate],
            label = buildSeriesLabel(type, patternType, weekdays, row[EventSeriesTable.intervalDays])
        )
    }

    // Human label e.g. "Training · Mon, Wed · weekly" (design §15).
    private fun buildSeriesLabel(
        type: String,
        patternType: String,
        weekdays: List<Short>?,
        intervalDays: Int?
    ): String {
        val typeLabel = type.replaceFirstChar { it.uppercase() }
        val parts = mutableListOf(typeLabel)
        if (!weekdays.isNullOrEmpty()) {
            parts.add(weekdays.sorted().joinToString(", ") { WEEKDAY_NAMES.getOrElse(it.toInt()) { _ -> "?" } })
        }
        val cadence = when (patternType) {
            "custom" -> if (intervalDays != null) "every $intervalDays days" else patternType
            else -> patternType
        }
        parts.add(cadence)
        return parts.joinToString(" · ")
    }

    override suspend fun listSyncedExternalGameIds(clubId: UUID): List<Long> = transaction {
        (EventsTable innerJoin EventTeamsTable innerJoin TeamsTable)
            .select(EventsTable.externalGameId)
            .where {
                (TeamsTable.clubId eq clubId) and
                    (EventsTable.externalStatus eq "synced") and
                    EventsTable.externalGameId.isNotNull()
            }
            .mapNotNull { it[EventsTable.externalGameId] }
            .distinct()
    }

    override suspend fun hasSyncedGameWithin(clubId: UUID, windowStart: Instant, windowEnd: Instant): Boolean = transaction {
        (EventsTable innerJoin EventTeamsTable innerJoin TeamsTable)
            .select(EventsTable.id)
            .where {
                (TeamsTable.clubId eq clubId) and
                    (EventsTable.externalStatus eq "synced") and
                    (EventsTable.startAt greaterEq windowStart) and
                    (EventsTable.startAt lessEq windowEnd)
            }
            .limit(1)
            .any()
    }

    override suspend fun listAwaitingCheckInForUser(userId: UUID): List<EventWithTeams> = transaction {
        val now = Instant.now()

        // Resolve teams the user coaches directly
        val coachedTeamIds = TeamRolesTable.select(TeamRolesTable.teamId)
            .where { (TeamRolesTable.userId eq userId) and (TeamRolesTable.role eq "coach") }
            .map { it[TeamRolesTable.teamId] }

        // Resolve teams from clubs where user is club_manager
        val managedClubIds = ClubRolesTable.select(ClubRolesTable.clubId)
            .where { (ClubRolesTable.userId eq userId) and (ClubRolesTable.role eq "club_manager") }
            .map { it[ClubRolesTable.clubId] }
        val clubManagedTeamIds = if (managedClubIds.isNotEmpty()) {
            TeamsTable.select(TeamsTable.id)
                .where { (TeamsTable.clubId inList managedClubIds) and TeamsTable.archivedAt.isNull() }
                .map { it[TeamsTable.id] }
        } else emptyList()

        val relevantTeamIds = (coachedTeamIds + clubManagedTeamIds).distinct()
        if (relevantTeamIds.isEmpty()) return@transaction emptyList()

        // Events: end_at < now, check_in_completed_at IS NULL, not cancelled, on a relevant team
        val eventRows = (EventsTable innerJoin EventTeamsTable)
            .selectAll()
            .where {
                (EventTeamsTable.teamId inList relevantTeamIds) and
                (EventsTable.endAt less now) and
                EventsTable.checkInCompletedAt.isNull() and
                (EventsTable.status neq EventStatus.cancelled)
            }
            .map(::rowToEvent)

        val uniqueEventIds = eventRows.map { it.id }.distinct()
        if (uniqueEventIds.isEmpty()) return@transaction emptyList()

        val counts = presentCountFor(uniqueEventIds)
        uniqueEventIds.mapNotNull { eid ->
            val event = eventRows.firstOrNull { it.id == eid } ?: return@mapNotNull null
            val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
                .where { EventTeamsTable.eventId eq eid }
                .map { it[EventTeamsTable.teamId] }
            val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
                .where { EventSubgroupsTable.eventId eq eid }
                .map { it[EventSubgroupsTable.subgroupId] }
            val matchedTeams = (EventTeamsTable innerJoin TeamsTable)
                .select(TeamsTable.id, TeamsTable.name)
                .where {
                    (EventTeamsTable.eventId eq eid) and
                    (EventTeamsTable.teamId inList relevantTeamIds)
                }
                .map { MatchedTeam(id = it[TeamsTable.id], name = it[TeamsTable.name]) }
            EventWithTeams(
                event = event.copy(teamIds = teamIds, subgroupIds = subgroupIds, presentCount = counts[eid] ?: 0),
                matchedTeams = matchedTeams
            )
        }
    }

    private fun rowToSyncedEventRef(row: ResultRow): SyncedEventRef {
        val now = Instant.now()
        val startAt = row[EventsTable.startAt]
        val endAt = row[EventsTable.endAt]
        return SyncedEventRef(
            id = row[EventsTable.id],
            externalHash = row[EventsTable.externalHash],
            status = row[EventsTable.status].name,
            externalStatus = row[EventsTable.externalStatus],
            finished = now.isAfter(endAt),
            live = !now.isBefore(startAt) && now.isBefore(endAt)
        )
    }

    private fun rowToEventWithRelations(id: UUID): Event {
        val event = EventsTable.selectAll().where { EventsTable.id eq id }
            .map(::rowToEvent)
            .single()
        val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
            .where { EventTeamsTable.eventId eq id }
            .map { it[EventTeamsTable.teamId] }
        val subgroupIds = EventSubgroupsTable.select(EventSubgroupsTable.subgroupId)
            .where { EventSubgroupsTable.eventId eq id }
            .map { it[EventSubgroupsTable.subgroupId] }
        return event.copy(teamIds = teamIds, subgroupIds = subgroupIds)
    }

    /** Returns confirmed-response counts per event, used for the presence chip on event cards. */
    private fun presentCountFor(eventIds: List<UUID>): Map<UUID, Int> {
        if (eventIds.isEmpty()) return emptyMap()
        val cnt = AttendanceResponsesTable.eventId.count()
        return AttendanceResponsesTable
            .select(AttendanceResponsesTable.eventId, cnt)
            .where { (AttendanceResponsesTable.eventId inList eventIds) and (AttendanceResponsesTable.status eq "confirmed") }
            .groupBy(AttendanceResponsesTable.eventId)
            .associate { it[AttendanceResponsesTable.eventId] to it[cnt].toInt() }
    }

    private fun rowToEvent(row: ResultRow): Event {
        val startAt = row[EventsTable.startAt]
        val endAt = row[EventsTable.endAt]
        val responseDeadline = row[EventsTable.responseDeadline]
        val checkInCompletedAt = row[EventsTable.checkInCompletedAt]
        val defaultResponse = row[EventsTable.defaultResponse]
        val cutoff = responseDeadline ?: startAt
        val checkInStatus = deriveCheckInStatus(
            now = Instant.now(),
            cutoff = cutoff,
            endAt = endAt,
            completedAt = checkInCompletedAt
        )
        return Event(
            id = row[EventsTable.id],
            title = row[EventsTable.title],
            type = row[EventsTable.type].name,
            startAt = startAt,
            endAt = endAt,
            meetupAt = row[EventsTable.meetupAt],
            location = row[EventsTable.location],
            description = row[EventsTable.description],
            minAttendees = row[EventsTable.minAttendees],
            status = row[EventsTable.status].name,
            cancelledAt = row[EventsTable.cancelledAt],
            seriesId = row[EventsTable.seriesId],
            seriesSequence = row[EventsTable.seriesSequence],
            seriesOverride = row[EventsTable.seriesOverride],
            createdBy = row[EventsTable.createdBy],
            createdAt = row[EventsTable.createdAt],
            updatedAt = row[EventsTable.updatedAt],
            externalSource = row[EventsTable.externalSource],
            externalStatus = row[EventsTable.externalStatus],
            needsReview = row[EventsTable.needsReview],
            checkInCompletedAt = checkInCompletedAt,
            defaultResponse = defaultResponse,
            checkInStatus = checkInStatus
        )
    }

    private fun rowToEventSeries(row: ResultRow) = EventSeries(
        id = row[EventSeriesTable.id],
        patternType = row[EventSeriesTable.patternType].name,
        weekdays = row[EventSeriesTable.weekdays],
        intervalDays = row[EventSeriesTable.intervalDays],
        seriesStartDate = row[EventSeriesTable.seriesStartDate],
        seriesEndDate = row[EventSeriesTable.seriesEndDate],
        templateStartTime = row[EventSeriesTable.templateStartTime],
        templateEndTime = row[EventSeriesTable.templateEndTime],
        templateMeetupTime = row[EventSeriesTable.templateMeetupTime],
        templateTitle = row[EventSeriesTable.templateTitle],
        templateType = row[EventSeriesTable.templateType].name,
        templateLocation = row[EventSeriesTable.templateLocation],
        templateDescription = row[EventSeriesTable.templateDescription],
        templateMinAttendees = row[EventSeriesTable.templateMinAttendees],
        createdBy = row[EventSeriesTable.createdBy],
        createdAt = row[EventSeriesTable.createdAt]
    )
}
