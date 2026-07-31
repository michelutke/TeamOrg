package ch.teamorg.infra.nds

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.domain.models.NdsConflictDate
import ch.teamorg.domain.models.NdsConflictGroup
import ch.teamorg.domain.models.NdsSeries
import ch.teamorg.domain.models.ParsedActivity
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Groups Anwesenheitsliste activities into weekly series / one-offs, and finds existing TeamOrg
 * events on the target team that collide with those dates (same date + mapped event type),
 * excluding events the NDS import itself created (those are re-import updates, not conflicts).
 * See docs/nds-import-export-design.md §7.
 */
class NdsImportPlanner {

    private val MIN_OCCURRENCES_FOR_SERIES = 3

    // weekdays array convention: 0=Mon..6=Sun (matches EventRepositoryImpl).
    private fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    fun series(activities: List<ParsedActivity>): List<NdsSeries> {
        val groups = activities.groupBy { Triple(weekdayIndex(it.date), it.symbol.uppercase(), it.durationMin) }
        val result = mutableListOf<NdsSeries>()
        for ((key, acts) in groups) {
            val (weekday, symbol, durationMin) = key
            val dates = acts.map { it.date }.sorted()
            if (acts.size >= MIN_OCCURRENCES_FOR_SERIES) {
                result.add(
                    NdsSeries(
                        seriesKey = "$weekday-$symbol-${durationMin ?: 0}",
                        weekday = weekday,
                        symbol = symbol,
                        durationMin = durationMin,
                        dates = dates,
                        count = acts.size
                    )
                )
            } else {
                for (date in dates) {
                    result.add(
                        NdsSeries(
                            seriesKey = "single-$date-$symbol",
                            weekday = weekday,
                            symbol = symbol,
                            durationMin = durationMin,
                            dates = listOf(date),
                            count = 1
                        )
                    )
                }
            }
        }
        return result
    }

    fun conflicts(teamId: UUID, series: List<NdsSeries>): List<NdsConflictGroup> {
        val allDates = series.flatMap { it.dates }.toSet()
        if (allDates.isEmpty()) return emptyList()

        val seriesKeyFor = HashMap<Pair<LocalDate, String>, String>()
        for (s in series) {
            val eventType = NdsRules.aktivitaetstypToEventType(NdsRules.symbolToAktivitaetstyp(s.symbol))
            for (date in s.dates) seriesKeyFor[date to eventType] = s.seriesKey
        }

        data class Match(val seriesKey: String, val date: LocalDate, val eventId: UUID, val title: String, val start: String)

        val matches = mutableListOf<Match>()
        transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where { (EventTeamsTable.teamId eq teamId) and (EventsTable.status neq EventStatus.cancelled) }
                .forEach { row ->
                    if (row[EventsTable.externalSource] == "nds") return@forEach
                    val date = row[EventsTable.startAt].atZone(ZoneOffset.UTC).toLocalDate()
                    if (date !in allDates) return@forEach
                    val eventType = row[EventsTable.type].name
                    val seriesKey = seriesKeyFor[date to eventType] ?: return@forEach
                    matches.add(Match(seriesKey, date, row[EventsTable.id], row[EventsTable.title], row[EventsTable.startAt].toString()))
                }
        }
        if (matches.isEmpty()) return emptyList()

        // One grouped COUNT query for every conflicting event's RSVP count, instead of per-event N+1.
        val rsvpCountByEvent = transaction {
            AttendanceResponsesTable
                .select(AttendanceResponsesTable.eventId, AttendanceResponsesTable.eventId.count())
                .where { AttendanceResponsesTable.eventId inList matches.map { it.eventId }.distinct() }
                .groupBy(AttendanceResponsesTable.eventId)
                .associate { it[AttendanceResponsesTable.eventId] to it[AttendanceResponsesTable.eventId.count()].toInt() }
        }

        val grouped = LinkedHashMap<String, MutableList<NdsConflictDate>>()
        for (m in matches) {
            grouped.getOrPut(m.seriesKey) { mutableListOf() }.add(
                NdsConflictDate(
                    date = m.date,
                    existingEventId = m.eventId.toString(),
                    existingEventTitle = m.title,
                    existingEventStart = m.start,
                    rsvpCount = rsvpCountByEvent[m.eventId] ?: 0
                )
            )
        }
        return grouped.map { (key, dates) -> NdsConflictGroup(key, dates.sortedBy { it.date }) }
    }
}
