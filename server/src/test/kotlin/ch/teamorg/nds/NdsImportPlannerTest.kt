package ch.teamorg.nds

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.NdsSeriesTime
import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.infra.nds.NdsEventImporter
import ch.teamorg.infra.nds.NdsImportPlanner
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NdsImportPlannerTest : IntegrationTestBase() {

    private val planner = NdsImportPlanner()

    private fun activity(date: LocalDate, symbol: String = "T", durationMin: Int? = 90, col: Int = 0) =
        ParsedActivity(date = date, weekday = null, kw = null, symbol = symbol, durationMin = durationMin, fokus = null, columnIndex = col)

    @Test
    fun `MO plus MI sample groups into two weekly series`() {
        val activities = NdsTestFixtures.ACTIVITY_DATES.mapIndexed { i, date -> activity(date, col = i) }
        val series = planner.series(activities)

        assertEquals(2, series.size)
        assertTrue(series.all { it.count == 4 })
        assertTrue(series.all { !it.seriesKey.startsWith("single-") })
        val weekdays = series.map { it.weekday }.toSet()
        assertEquals(setOf(0, 2), weekdays) // Monday=0, Wednesday=2
    }

    @Test
    fun `fewer than three occurrences of a weekday-symbol-duration group become one-offs`() {
        val activities = listOf(
            activity(LocalDate.of(2026, 8, 3), col = 0),  // Monday
            activity(LocalDate.of(2026, 8, 10), col = 1), // Monday
            activity(LocalDate.of(2026, 8, 5), symbol = "W", col = 2) // Wednesday, different symbol
        )
        val series = planner.series(activities)

        assertEquals(3, series.size)
        assertTrue(series.all { it.count == 1 })
        assertTrue(series.all { it.seriesKey.startsWith("single-") })
        assertTrue(series.any { it.seriesKey == "single-2026-08-03-T" })
        assertTrue(series.any { it.seriesKey == "single-2026-08-05-W" })
    }

    @Test
    fun `conflicts excludes cancelled and nds-source events but flags matching active ones`() = withTeamorgTestApplication {
        startApplication()
        val date = LocalDate.of(2026, 9, 7) // a Monday
        val (teamId, activeEventId) = transaction {
            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[email] = "planner-owner-${UUID.randomUUID()}@example.com"
                it[passwordHash] = "!"
                it[displayName] = "Planner Owner"
            }
            val clubId = UUID.randomUUID()
            ch.teamorg.db.tables.ClubsTable.insert {
                it[id] = clubId
                it[name] = "Planner Club"
                it[sportType] = "volleyball"
            }
            val teamId = UUID.randomUUID()
            TeamsTable.insert {
                it[id] = teamId
                it[TeamsTable.clubId] = clubId
                it[name] = "Planner Team"
            }
            TeamRolesTable.insert {
                it[TeamRolesTable.userId] = userId
                it[TeamRolesTable.teamId] = teamId
                it[role] = "coach"
            }

            fun insertEvent(status: EventStatus, externalSource: String?, type: EventType = EventType.training): UUID {
                val eventId = EventsTable.insert {
                    it[title] = "Existing Event"
                    it[EventsTable.type] = type
                    it[startAt] = date.atStartOfDay().toInstant(ZoneOffset.UTC)
                    it[endAt] = date.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
                    it[EventsTable.status] = status
                    it[EventsTable.externalSource] = externalSource
                    it[createdBy] = userId
                } get EventsTable.id
                EventTeamsTable.insert {
                    it[EventTeamsTable.eventId] = eventId
                    it[EventTeamsTable.teamId] = teamId
                }
                return eventId
            }

            val active = insertEvent(EventStatus.active, null)
            insertEvent(EventStatus.cancelled, null) // excluded: cancelled
            insertEvent(EventStatus.active, "nds") // excluded: nds re-import, not a conflict
            teamId to active
        }

        val series = planner.series(listOf(activity(date)))
        assertEquals(1, series.size) // single one-off (only 1 occurrence)

        val conflicts = planner.conflicts(teamId, series)
        assertEquals(1, conflicts.size)
        val group = conflicts.single()
        assertEquals(series.single().seriesKey, group.seriesKey)
        assertEquals(1, group.dates.size)
        assertEquals(date, group.dates.single().date)
        assertEquals(activeEventId.toString(), group.dates.single().existingEventId)
    }

    @Test
    fun `conflict dates carry the existing event's rsvpCount`() = withTeamorgTestApplication {
        startApplication()
        val date = LocalDate.of(2026, 9, 14) // a Monday
        val (teamId, activeEventId) = transaction {
            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[email] = "planner-rsvp-owner-${UUID.randomUUID()}@example.com"
                it[passwordHash] = "!"
                it[displayName] = "Planner Rsvp Owner"
            }
            val responder = UUID.randomUUID()
            UsersTable.insert {
                it[id] = responder
                it[email] = "planner-rsvp-responder-${UUID.randomUUID()}@example.com"
                it[passwordHash] = "!"
                it[displayName] = "Planner Rsvp Responder"
            }
            val clubId = UUID.randomUUID()
            ch.teamorg.db.tables.ClubsTable.insert {
                it[id] = clubId
                it[name] = "Planner Rsvp Club"
                it[sportType] = "volleyball"
            }
            val teamId = UUID.randomUUID()
            TeamsTable.insert {
                it[id] = teamId
                it[TeamsTable.clubId] = clubId
                it[name] = "Planner Rsvp Team"
            }
            val eventId = EventsTable.insert {
                it[title] = "Existing Event"
                it[EventsTable.type] = EventType.training
                it[startAt] = date.atStartOfDay().toInstant(ZoneOffset.UTC)
                it[endAt] = date.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
                it[createdBy] = userId
            } get EventsTable.id
            EventTeamsTable.insert {
                it[EventTeamsTable.eventId] = eventId
                it[EventTeamsTable.teamId] = teamId
            }
            AttendanceResponsesTable.insert {
                it[AttendanceResponsesTable.eventId] = eventId
                it[AttendanceResponsesTable.userId] = responder
                it[status] = "confirmed"
            }
            teamId to eventId
        }

        val series = planner.series(listOf(activity(date)))
        val conflicts = planner.conflicts(teamId, series)

        assertEquals(1, conflicts.single().dates.size)
        assertEquals(1, conflicts.single().dates.single().rsvpCount)
        assertEquals(activeEventId.toString(), conflicts.single().dates.single().existingEventId)
    }

    // Regression: NdsEventImporter used to key its ParsedActivity lookup by date alone when
    // reconstructing planner.series() groups back into concrete activities. Two activity columns
    // on the SAME date with different symbols (e.g. a Training and a Wettkampf the same day) made
    // that lookup collide and silently drop/misassign one of them.
    @Test
    fun `import handles two same-date activities with different symbols without cross-assigning types`() = withTeamorgTestApplication {
        startApplication()
        val mondays = listOf(
            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)
        )
        val (teamId, createdBy) = transaction {
            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[email] = "importer-owner-${UUID.randomUUID()}@example.com"
                it[passwordHash] = "!"
                it[displayName] = "Importer Owner"
            }
            val clubId = UUID.randomUUID()
            ch.teamorg.db.tables.ClubsTable.insert {
                it[id] = clubId
                it[name] = "Importer Club"
                it[sportType] = "volleyball"
            }
            val teamId = UUID.randomUUID()
            TeamsTable.insert {
                it[id] = teamId
                it[TeamsTable.clubId] = clubId
                it[name] = "Importer Team"
            }
            teamId to userId
        }

        // Each Monday gets both a Training ('T') and a Wettkampf ('W') activity column, four times
        // over → both symbols independently qualify as a weekly series (count == 4).
        val activities = mondays.flatMapIndexed { i, date ->
            listOf(
                ParsedActivity(date = date, weekday = "MO", kw = null, symbol = "T", durationMin = 90, fokus = null, columnIndex = i * 2),
                ParsedActivity(date = date, weekday = "MO", kw = null, symbol = "W", durationMin = 60, fokus = null, columnIndex = i * 2 + 1)
            )
        }
        val parsed = ParsedAnwesenheitsliste(angebotId = "dual-symbol-1", kursName = "Dual", activities = activities, members = emptyList())

        val planner = NdsImportPlanner()
        val seriesTimes = planner.series(activities).associate { it.seriesKey to NdsSeriesTime(it.seriesKey, "18:00", "19:30") }
        NdsEventImporter(planner).import(teamId, parsed, attendanceMode = "discard", createdBy = createdBy, seriesTimes = seriesTimes)

        val events = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }
                .map { it[EventTeamsTable.eventId] }
            EventsTable.selectAll().where { EventsTable.id inList ids }.toList()
        }
        assertEquals(8, events.size)

        val byDate = events.groupBy { it[EventsTable.startAt].atZone(ZoneOffset.UTC).toLocalDate() }
        for (date in mondays) {
            val dayEvents = byDate.getValue(date)
            assertEquals(2, dayEvents.size, "expected both activities for $date")
            assertEquals(setOf(EventType.training, EventType.match), dayEvents.map { it[EventsTable.type] }.toSet())
            assertEquals(setOf("T", "W"), dayEvents.map { it[EventsTable.ndsSymbol] }.toSet())
        }
    }
}
