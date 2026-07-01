package ch.teamorg.infra.nds

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventSeriesTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.PatternType
import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
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
 * Imports the activity columns of a parsed Anwesenheitsliste into TeamOrg events, detecting
 * recurring (weekly) patterns into event_series, and optionally writing the documented
 * attendances. See docs/nds-import-export-design.md §7.
 *
 * The Anwesenheitsliste carries no time of day, so events get a placeholder start time; the coach
 * must set the real time + location before exporting (pre-flight flags this). Times use the app's
 * UTC-as-local convention (matching EventRepositoryImpl).
 */
class NdsEventImporter {

    // Placeholder start time for imported activities (no time in the sheet).
    private val PLACEHOLDER_START: LocalTime = LocalTime.of(18, 0)
    private val MIN_OCCURRENCES_FOR_SERIES = 3

    fun import(teamId: UUID, parsed: ParsedAnwesenheitsliste, attendanceMode: String, createdBy: UUID): NdsImportCounts = transaction {
        // Existing NDS event dates (per symbol) for idempotent re-import.
        val existingKeys = (EventsTable innerJoin EventTeamsTable)
            .select(EventsTable.startAt, EventsTable.ndsSymbol)
            .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.externalSource eq "nds") }
            .map { dateKey(it[EventsTable.startAt]) to (it[EventsTable.ndsSymbol] ?: "") }
            .toSet()
        val firstImport = existingKeys.isEmpty()

        val toCreate = parsed.activities.filter { (it.date to it.symbol.uppercase()) !in existingKeys }

        // Group into weekly series candidates only on a fresh import (avoids duplicate series).
        val seriesGroups: List<List<ParsedActivity>>
        val singles: List<ParsedActivity>
        if (firstImport) {
            val groups = toCreate.groupBy { Triple(weekdayShort(it.date), it.symbol.uppercase(), it.durationMin) }
            seriesGroups = groups.values.filter { it.size >= MIN_OCCURRENCES_FOR_SERIES }
            singles = groups.values.filter { it.size < MIN_OCCURRENCES_FOR_SERIES }.flatten()
        } else {
            seriesGroups = emptyList()
            singles = toCreate
        }

        // Map (date -> eventId) for attendance attachment; seeded with anything we create/find.
        val dateToEvent = HashMap<LocalDate, UUID>()
        var created = 0

        for (group in seriesGroups) {
            val sorted = group.sortedBy { it.date }
            val seriesId = createSeries(sorted, parsed, createdBy)
            sorted.forEachIndexed { idx, act ->
                val eid = insertEvent(teamId, act, parsed, createdBy, seriesId, idx)
                dateToEvent[act.date] = eid
                created++
            }
        }
        for (act in singles) {
            val eid = insertEvent(teamId, act, parsed, createdBy, seriesId = null, sequence = null)
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

            for (m in parsed.members) {
                val userId = memberUserByName[m.lastName.lowercase() to m.firstName.lowercase()] ?: continue
                // For EVERY dated NDS event of the team: attended → confirmed, otherwise → declined
                // (excused). insertIgnore keeps existing rows (e.g. player self-responses) untouched.
                for ((date, eventId) in dateToEventFull) {
                    val attended = date in m.attendedDates
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
        sequence: Int?
    ): UUID {
        val typ = NdsRules.symbolToAktivitaetstyp(act.symbol)
        val eventType = NdsRules.aktivitaetstypToEventType(typ)
        val durationMin = act.durationMin ?: 90
        val start = act.date.atTime(PLACEHOLDER_START).toInstant(ZoneOffset.UTC)
        val end = act.date.atTime(PLACEHOLDER_START.plusMinutes(durationMin.toLong())).toInstant(ZoneOffset.UTC)
        val title = parsed.kursName?.takeIf { it.isNotBlank() } ?: typ

        val eventId = EventsTable.insert {
            it[EventsTable.title] = title
            it[EventsTable.type] = EventType.valueOf(eventType)
            it[EventsTable.startAt] = start
            it[EventsTable.endAt] = end
            it[EventsTable.location] = null
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

    private fun createSeries(activities: List<ParsedActivity>, parsed: ParsedAnwesenheitsliste, createdBy: UUID): UUID {
        val first = activities.first()
        val durationMin = first.durationMin ?: 90
        val typ = NdsRules.symbolToAktivitaetstyp(first.symbol)
        val eventType = NdsRules.aktivitaetstypToEventType(typ)
        return EventSeriesTable.insert {
            it[EventSeriesTable.patternType] = PatternType.weekly
            it[EventSeriesTable.weekdays] = listOf(weekdayShort(first.date))
            it[EventSeriesTable.intervalDays] = null
            it[EventSeriesTable.seriesStartDate] = activities.minOf { a -> a.date }
            it[EventSeriesTable.seriesEndDate] = activities.maxOf { a -> a.date }
            it[EventSeriesTable.templateStartTime] = PLACEHOLDER_START
            it[EventSeriesTable.templateEndTime] = PLACEHOLDER_START.plusMinutes(durationMin.toLong())
            it[EventSeriesTable.templateMeetupTime] = null
            it[EventSeriesTable.templateTitle] = parsed.kursName?.takeIf { n -> n.isNotBlank() } ?: typ
            it[EventSeriesTable.templateType] = EventType.valueOf(eventType)
            it[EventSeriesTable.templateLocation] = null
            it[EventSeriesTable.templateDescription] = null
            it[EventSeriesTable.templateMinAttendees] = null
            it[EventSeriesTable.createdBy] = createdBy
        } get EventSeriesTable.id
    }

    // weekdays array convention: 0=Mon..6=Sun (matches EventRepositoryImpl).
    private fun weekdayShort(date: LocalDate): Short = (date.dayOfWeek.value - 1).toShort()

    private fun dateKey(instant: Instant): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
}
