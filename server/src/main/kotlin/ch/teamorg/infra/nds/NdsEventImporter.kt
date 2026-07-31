package ch.teamorg.infra.nds

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventSeriesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.PatternType
import ch.teamorg.domain.models.NdsMapping
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.NdsSeriesTime
import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.domain.repositories.NdsMemberOps
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

data class NdsImportCounts(val eventsCreated: Int, val attendanceImported: Int)

/**
 * Imports a parsed Anwesenheitsliste (member rows, activities, attendance) into a team in ONE
 * transaction: member mapping decisions, then event/series creation (detecting recurring weekly
 * patterns into event_series), then attendance — a thrown exception rolls all of it back. See
 * docs/nds-import-export-design.md §7.
 *
 * Event times come from the wizard's [NdsSeriesTime] per series (start/end/location) — the
 * Anwesenheitsliste itself carries no time of day. Times use the app's UTC-as-local convention
 * (matching EventRepositoryImpl).
 */
class NdsEventImporter(private val planner: NdsImportPlanner) {

    private val MIN_OCCURRENCES_FOR_SERIES = 3

    fun import(
        teamId: UUID,
        parsed: ParsedAnwesenheitsliste?,
        attendanceMode: String,
        createdBy: UUID,
        importEvents: Boolean = true,
        mergedMemberRows: List<NdsMemberInput> = emptyList(),
        mappingsByRowKey: Map<String, NdsMapping> = emptyMap(),
        seriesTimes: Map<String, NdsSeriesTime> = emptyMap(),
        resolutions: Map<LocalDate, String> = emptyMap()
    ): NdsImportCounts = transaction {
        // Member rows: apply the wizard's per-row decision (default "create" preserves the
        // pre-wizard auto-upsert behavior for any row without an explicit mapping).
        for (row in mergedMemberRows) {
            val rowKey = NdsMemberMatcher.rowKey(row.funktion, row.lastName, row.firstName)
            when (mappingsByRowKey[rowKey]?.action) {
                "map" -> {
                    val userId = UUID.fromString(mappingsByRowKey.getValue(rowKey).userId!!)
                    NdsMemberOps.applyMappingSync(teamId, row, userId)
                }
                "skip" -> {}
                else -> {
                    val memberId = NdsMemberOps.upsertOneSync(teamId, row)
                    NdsMemberOps.ensureUserAndRoleSync(memberId, teamId, row.funktion, row.firstName, row.lastName)
                }
            }
        }

        if (parsed == null || !importEvents) return@transaction NdsImportCounts(0, 0)

        // Existing NDS event dates (per symbol) for idempotent re-import.
        val existingKeys = (EventsTable innerJoin EventTeamsTable)
            .select(EventsTable.startAt, EventsTable.ndsSymbol)
            .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.externalSource eq "nds") }
            .map { dateKey(it[EventsTable.startAt]) to (it[EventsTable.ndsSymbol] ?: "") }
            .toSet()
        val firstImport = existingKeys.isEmpty()

        // Recomputed fresh (never trust a client-supplied conflict list) to find the existing
        // event id behind each conflicting date, keyed by seriesKey for the createSeries/insertEvent
        // time lookup below.
        val ndsSeriesAll = planner.series(parsed.activities)
        val seriesKeyByDateSymbol = ndsSeriesAll.flatMap { s -> s.dates.map { d -> (d to s.symbol) to s.seriesKey } }.toMap()
        val conflictEventIdByDate = planner.conflicts(teamId, ndsSeriesAll)
            .flatMap { g -> g.dates.map { it.date to it.existingEventId } }
            .toMap()

        // Map (date -> eventId) for attendance attachment; seeded with anything we create/find.
        val dateToEvent = HashMap<LocalDate, UUID>()

        // keep=nds → cancel the existing TeamOrg event (no notifications) before the NDS event is
        // inserted below. If the event is shared with other teams, cancelling it would affect them
        // too — instead just detach this team from it, leaving it active for the other team(s).
        // keep=teamorg → no NDS event created; attendance targets the existing one.
        for ((date, existingEventId) in conflictEventIdByDate) {
            when (resolutions[date] ?: "teamorg") {
                "nds" -> {
                    val eventUuid = UUID.fromString(existingEventId)
                    val teamCount = EventTeamsTable.selectAll()
                        .where { EventTeamsTable.eventId eq eventUuid }.count()
                    if (teamCount > 1) {
                        EventTeamsTable.deleteWhere { (EventTeamsTable.eventId eq eventUuid) and (EventTeamsTable.teamId eq teamId) }
                    } else {
                        EventsTable.update({ EventsTable.id eq eventUuid }) {
                            it[status] = EventStatus.cancelled
                        }
                    }
                }
                else -> dateToEvent[date] = UUID.fromString(existingEventId)
            }
        }

        val toCreate = parsed.activities.filter { act ->
            (act.date to act.symbol.uppercase()) !in existingKeys &&
                (conflictEventIdByDate[act.date] == null || (resolutions[act.date] ?: "teamorg") != "teamorg")
        }

        // Group into weekly series candidates only on a fresh import (avoids duplicate series).
        // Grouping itself comes from NdsImportPlanner.series so parse and import agree.
        val seriesGroups: List<List<ParsedActivity>>
        val singles: List<ParsedActivity>
        if (firstImport) {
            // Keyed by date+symbol, not date alone: two activity columns can share a date with
            // different symbols (e.g. a Training and a Wettkampf on the same day).
            val dateSymbolToActivity = toCreate.associateBy { it.date to it.symbol.uppercase() }
            val ndsSeries = planner.series(toCreate)
            seriesGroups = ndsSeries.filter { it.count >= MIN_OCCURRENCES_FOR_SERIES }
                .map { s -> s.dates.mapNotNull { dateSymbolToActivity[it to s.symbol] } }
            singles = ndsSeries.filter { it.count < MIN_OCCURRENCES_FOR_SERIES }
                .flatMap { s -> s.dates.mapNotNull { dateSymbolToActivity[it to s.symbol] } }
        } else {
            seriesGroups = emptyList()
            singles = toCreate
        }

        var created = 0

        for (group in seriesGroups) {
            val sorted = group.sortedBy { it.date }
            val seriesKey = seriesKeyByDateSymbol.getValue(sorted.first().date to sorted.first().symbol.uppercase())
            val time = seriesTimes.getValue(seriesKey)
            val seriesId = createSeries(sorted, parsed, createdBy, time)
            sorted.forEachIndexed { idx, act ->
                val eid = insertEvent(teamId, act, parsed, createdBy, seriesId, idx, time)
                dateToEvent[act.date] = eid
                created++
            }
        }
        for (act in singles) {
            val seriesKey = seriesKeyByDateSymbol.getValue(act.date to act.symbol.uppercase())
            val time = seriesTimes.getValue(seriesKey)
            val eid = insertEvent(teamId, act, parsed, createdBy, seriesId = null, sequence = null, time = time)
            dateToEvent[act.date] = eid
            created++
        }

        var attendance = 0
        if (attendanceMode == "keep") {
            // (date -> eventId) for every dated NDS event of this team, including pre-existing ones
            // so re-imports still attach. This is the full event set each member is enumerated over.
            val allNds = (EventsTable innerJoin EventTeamsTable)
                .select(EventsTable.id, EventsTable.startAt)
                .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.externalSource eq "nds") }
                .associate { dateKey(it[EventsTable.startAt]) to it[EventsTable.id] }
            val dateToEventFull = allNds + dateToEvent

            // Match by name (birthdate is unreliable across the NDS exports — see upsertOne).
            val memberUserByName = NdsMembersTable
                .select(NdsMembersTable.lastName, NdsMembersTable.firstName, NdsMembersTable.userId)
                .where { NdsMembersTable.teamId eq teamId }
                .associate {
                    (it[NdsMembersTable.lastName].lowercase() to it[NdsMembersTable.firstName].lowercase()) to
                        it[NdsMembersTable.userId]
                }

            val today = LocalDate.now(ZoneOffset.UTC)
            for (m in parsed.members) {
                val userId = memberUserByName[m.lastName.lowercase() to m.firstName.lowercase()] ?: continue
                // For EVERY dated NDS event of the team: attended → confirmed, otherwise → declined
                // (excused). insertIgnore keeps existing rows (e.g. player self-responses) untouched.
                // Non-attended dates only get a declined write once past — pre-declining a real
                // user's future event would be wrong (they haven't had a chance to respond yet).
                for ((date, eventId) in dateToEventFull) {
                    val attended = date in m.attendedDates
                    if (!attended && !date.isBefore(today)) continue
                    val inserted = AttendanceResponsesTable.insertIgnore {
                        it[AttendanceResponsesTable.eventId] = eventId
                        it[AttendanceResponsesTable.userId] = userId
                        it[AttendanceResponsesTable.status] = if (attended) "confirmed" else "declined"
                        it[AttendanceResponsesTable.unexcused] = false
                        it[AttendanceResponsesTable.manualOverride] = false
                    }.insertedCount
                    if (attended) attendance += inserted
                }
            }
        }

        // Auto-finalize past imported events; future ones stay open for coach check-in.
        val now = Instant.now()
        (EventsTable innerJoin EventTeamsTable)
            .select(EventsTable.id)
            .where {
                (EventTeamsTable.teamId eq teamId) and
                    (EventsTable.externalSource eq "nds") and
                    (EventsTable.startAt less now) and
                    EventsTable.checkInCompletedAt.isNull()
            }
            .map { it[EventsTable.id] }
            .let { pastEventIds ->
                if (pastEventIds.isNotEmpty()) {
                    EventsTable.update({ EventsTable.id inList pastEventIds }) {
                        it[EventsTable.checkInCompletedAt] = now
                    }
                }
            }

        NdsImportCounts(eventsCreated = created, attendanceImported = attendance)
    }

    private fun insertEvent(
        teamId: UUID,
        act: ParsedActivity,
        parsed: ParsedAnwesenheitsliste,
        createdBy: UUID,
        seriesId: UUID?,
        sequence: Int?,
        time: NdsSeriesTime
    ): UUID {
        val typ = NdsRules.symbolToAktivitaetstyp(act.symbol)
        val eventType = NdsRules.aktivitaetstypToEventType(typ)
        val start = act.date.atTime(LocalTime.parse(time.startTime)).toInstant(ZoneOffset.UTC)
        val end = act.date.atTime(LocalTime.parse(time.endTime)).toInstant(ZoneOffset.UTC)
        val title = parsed.kursName?.takeIf { it.isNotBlank() } ?: typ

        val eventId = EventsTable.insert {
            it[EventsTable.title] = title
            it[EventsTable.type] = EventType.valueOf(eventType)
            it[EventsTable.startAt] = start
            it[EventsTable.endAt] = end
            it[EventsTable.location] = time.location
            it[EventsTable.seriesId] = seriesId
            it[EventsTable.seriesSequence] = sequence
            it[EventsTable.externalSource] = "nds"
            it[EventsTable.ndsSymbol] = act.symbol.uppercase()
            it[EventsTable.createdBy] = createdBy
        } get EventsTable.id

        EventTeamsTable.insert {
            it[EventTeamsTable.eventId] = eventId
            it[EventTeamsTable.teamId] = teamId
        }
        return eventId
    }

    private fun createSeries(
        activities: List<ParsedActivity>,
        parsed: ParsedAnwesenheitsliste,
        createdBy: UUID,
        time: NdsSeriesTime
    ): UUID {
        val first = activities.first()
        val typ = NdsRules.symbolToAktivitaetstyp(first.symbol)
        val eventType = NdsRules.aktivitaetstypToEventType(typ)
        return EventSeriesTable.insert {
            it[EventSeriesTable.patternType] = PatternType.weekly
            it[EventSeriesTable.weekdays] = listOf(weekdayShort(first.date))
            it[EventSeriesTable.intervalDays] = null
            it[EventSeriesTable.seriesStartDate] = activities.minOf { a -> a.date }
            it[EventSeriesTable.seriesEndDate] = activities.maxOf { a -> a.date }
            it[EventSeriesTable.templateStartTime] = LocalTime.parse(time.startTime)
            it[EventSeriesTable.templateEndTime] = LocalTime.parse(time.endTime)
            it[EventSeriesTable.templateMeetupTime] = null
            it[EventSeriesTable.templateTitle] = parsed.kursName?.takeIf { n -> n.isNotBlank() } ?: typ
            it[EventSeriesTable.templateType] = EventType.valueOf(eventType)
            it[EventSeriesTable.templateLocation] = time.location
            it[EventSeriesTable.templateDescription] = null
            it[EventSeriesTable.templateMinAttendees] = null
            it[EventSeriesTable.createdBy] = createdBy
        } get EventSeriesTable.id
    }

    // weekdays array convention: 0=Mon..6=Sun (matches EventRepositoryImpl).
    private fun weekdayShort(date: LocalDate): Short = (date.dayOfWeek.value - 1).toShort()

    private fun dateKey(instant: Instant): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
}
