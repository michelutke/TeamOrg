package ch.teamorg.nds

import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.infra.nds.NdsImportPlanner
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
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
}
