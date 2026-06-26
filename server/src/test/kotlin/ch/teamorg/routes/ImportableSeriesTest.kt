package ch.teamorg.routes

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.EventSeriesTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.PatternType
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.domain.models.ImportableSeriesResult
import ch.teamorg.infra.PushService
import ch.teamorg.infra.SVGame
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportableSeriesTest : IntegrationTestBase() {

    private class StubSwissVolleyClient : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> = emptyList()
        override suspend fun listGames(apiKey: String): List<SVGame> = emptyList()
    }

    private class StubPushService : PushService {
        override suspend fun sendToUsers(
            userIds: List<String>,
            title: String,
            body: String,
            data: Map<String, String>
        ) {
            // no-op
        }
    }

    private fun koinOverride() = module {
        single<SwissVolleyClient> { StubSwissVolleyClient() }
        single<PushService> { StubPushService() }
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val c = createJsonClient()
        return c.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    private suspend fun ApplicationTestBuilder.getImportable(teamId: UUID, token: String) =
        createJsonClient().get("/teams/$teamId/importable-series") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    // --- DB seeding -------------------------------------------------------------------

    private fun createClub(suffix: String): UUID = transaction {
        ClubsTable.insert { it[name] = "ImpClub-$suffix" } get ClubsTable.id
    }

    private fun createTeam(clubId: UUID, name: String, predecessorTeamId: UUID? = null): UUID =
        transaction {
            TeamsTable.insert {
                it[TeamsTable.clubId] = clubId
                it[TeamsTable.name] = name
                if (predecessorTeamId != null) it[TeamsTable.predecessorTeamId] = predecessorTeamId
            } get TeamsTable.id
        }

    private fun addCoach(teamId: UUID, userId: UUID) = transaction {
        TeamRolesTable.insert {
            it[TeamRolesTable.userId] = userId
            it[TeamRolesTable.teamId] = teamId
            it[TeamRolesTable.role] = "coach"
        }
    }

    /**
     * Creates a recurring series owned by [createdBy] plus one occurrence event linked to
     * [teamId] via event_teams (the repo discovers series through that join). Returns seriesId.
     */
    private fun seedSeries(
        teamId: UUID,
        createdBy: UUID,
        title: String,
        weekday: Short
    ): UUID = transaction {
        val seriesId = EventSeriesTable.insert {
            it[patternType] = PatternType.weekly
            it[weekdays] = listOf(weekday)
            it[seriesStartDate] = LocalDate.of(2026, 1, 5)
            it[seriesEndDate] = LocalDate.of(2026, 6, 30)
            it[templateStartTime] = LocalTime.of(18, 0)
            it[templateEndTime] = LocalTime.of(20, 0)
            it[templateMeetupTime] = LocalTime.of(17, 30)
            it[templateTitle] = title
            it[templateType] = EventType.training
            it[templateLocation] = "Gym"
            it[templateMinAttendees] = 6
            it[EventSeriesTable.createdBy] = createdBy
        } get EventSeriesTable.id

        val start = Instant.now().plus(Duration.ofDays(3))
        val eventId = EventsTable.insert {
            it[EventsTable.title] = title
            it[type] = EventType.training
            it[startAt] = start
            it[endAt] = start.plus(Duration.ofHours(2))
            it[EventsTable.seriesId] = seriesId
            it[seriesSequence] = 0
            it[EventsTable.createdBy] = createdBy
        } get EventsTable.id
        EventTeamsTable.insert {
            it[EventTeamsTable.eventId] = eventId
            it[EventTeamsTable.teamId] = teamId
        }
        seriesId
    }

    // --- tests ------------------------------------------------------------------------

    @Test
    fun `target with predecessor having two recurring series returns two templates`() =
        withTeamorgTestApplication(koinOverride()) {
            val coach = register("imp_pred_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val clubId = createClub("pred")

            // Predecessor team owns two recurring series.
            val predecessor = createTeam(clubId, "Predecessor")
            seedSeries(predecessor, coachId, "Training A", weekday = 1)
            seedSeries(predecessor, coachId, "Training B", weekday = 3)

            // Target points at the predecessor and is coached by the caller.
            val target = createTeam(clubId, "Target", predecessorTeamId = predecessor)
            addCoach(target, coachId)

            val response = getImportable(target, coach.token)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<ImportableSeriesResult>()

            assertEquals(2, body.series.size, "two templates from predecessor")
            assertFalse(body.hasOwnSeries, "target has no series of its own")
            assertEquals(predecessor.toString(), body.predecessorTeamId)
            assertEquals(setOf("Training A", "Training B"), body.series.map { it.templateTitle }.toSet())

            // Field fidelity on one template.
            val a = body.series.single { it.templateTitle == "Training A" }
            assertEquals("weekly", a.patternType)
            assertEquals("training", a.templateType)
            assertEquals(LocalTime.of(18, 0), a.templateStartTime)
            assertEquals(LocalTime.of(20, 0), a.templateEndTime)
            assertEquals(6, a.templateMinAttendees)
        }

    @Test
    fun `team with no predecessor returns empty series and hasOwnSeries reflects its own`() =
        withTeamorgTestApplication(koinOverride()) {
            val coach = register("imp_own_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val clubId = createClub("own")

            val team = createTeam(clubId, "OwnTeam")
            addCoach(team, coachId)
            seedSeries(team, coachId, "Own Training", weekday = 2)

            val response = getImportable(team, coach.token)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<ImportableSeriesResult>()

            assertTrue(body.series.isEmpty(), "no predecessor => nothing importable")
            assertTrue(body.hasOwnSeries, "team has its own series")
            assertEquals(null, body.predecessorTeamId)
        }

    @Test
    fun `non-coach caller gets 403`() =
        withTeamorgTestApplication(koinOverride()) {
            val coach = register("imp_403_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val clubId = createClub("403")
            val team = createTeam(clubId, "Team403")
            addCoach(team, coachId)

            // Outsider holds a valid JWT but no role on the team.
            val outsider = register("imp_403_outsider@example.com")
            val response = getImportable(team, outsider.token)
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
}
