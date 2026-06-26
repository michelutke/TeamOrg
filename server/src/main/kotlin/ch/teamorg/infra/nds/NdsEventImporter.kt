package ch.teamorg.infra.nds

import ch.teamorg.db.tables.AttendanceRecordsTable
import ch.teamorg.db.tables.EventSeriesTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.PatternType
import ch.teamorg.db.tables.RecordStatus
import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

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

    fun import(teamId: UUID, parsed: ParsedAnwesenheitsliste, attendanceMode: String, createdBy: UUID): Int = transaction {
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

        if (attendanceMode == "keep") {
            // Build (date -> eventId) including pre-existing events so re-imports still attach.
            val allNds = (EventsTable innerJoin EventTeamsTable)
                .select(EventsTable.id, EventsTable.startAt)
                .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.externalSource eq "nds") }
                .associate { dateKey(it[EventsTable.startAt]) to it[EventsTable.id] }

            val memberUserByIdentity = NdsMembersTable
                .select(NdsMembersTable.lastName, NdsMembersTable.firstName, NdsMembersTable.birthDate, NdsMembersTable.userId)
                .where { NdsMembersTable.teamId eq teamId }
                .associate {
                    Triple(it[NdsMembersTable.lastName], it[NdsMembersTable.firstName], it[NdsMembersTable.birthDate]) to
                        it[NdsMembersTable.userId]
                }

            for (m in parsed.members) {
                val userId = memberUserByIdentity[Triple(m.lastName, m.firstName, m.birthDate)] ?: continue
                for (date in m.attendedDates) {
                    val eventId = allNds[date] ?: dateToEvent[date] ?: continue
                    AttendanceRecordsTable.insertIgnore {
                        it[AttendanceRecordsTable.eventId] = eventId
                        it[AttendanceRecordsTable.userId] = userId
                        it[AttendanceRecordsTable.status] = RecordStatus.present
                        it[AttendanceRecordsTable.setBy] = createdBy
                    }
                }
            }
        }

        created
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
