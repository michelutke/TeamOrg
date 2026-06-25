package ch.teamorg.routes

import ch.teamorg.db.tables.ClubIntegrationsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.infra.PushService
import ch.teamorg.infra.SVGame
import ch.teamorg.infra.SVGameTeam
import ch.teamorg.infra.SVGameTeams
import ch.teamorg.infra.SVHall
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameSyncToggleTest : IntegrationTestBase() {

    private val EXTERNAL_SOURCE = "swissvolley"

    /** Stub client returning a single canned game so the immediate toggle-on sync has work. */
    private class StubSwissVolleyClient(var games: List<SVGame> = emptyList()) : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> = emptyList()
        override suspend fun listGames(apiKey: String): List<SVGame> = games
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

    private fun koinOverride(client: SwissVolleyClient) = module {
        single<SwissVolleyClient> { client }
        single<PushService> { StubPushService() }
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val c = createJsonClient()
        return c.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    private suspend fun ApplicationTestBuilder.createClubAndTeam(token: String, name: String): Pair<String, String> {
        val c = createJsonClient()
        val club = c.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("$name Club"))
        }.body<Club>()
        val team = c.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("$name Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    /** Seed a valid integration + an active SV link so the team can sync. */
    private fun seedSvLink(clubId: String, teamId: String, svTeamId: Int) = transaction {
        ClubIntegrationsTable.insert {
            it[ClubIntegrationsTable.clubId] = UUID.fromString(clubId)
            it[apiKey] = "valid-key"
            it[keyValid] = true
        }
        TeamSvLinksTable.insert {
            it[TeamSvLinksTable.teamId] = UUID.fromString(teamId)
            it[TeamSvLinksTable.svTeamId] = svTeamId
        }
    }

    /** Add [userId] to [teamId] as a plain coach (team-level role, no club_manager). */
    private fun addCoach(teamId: String, userId: String) = transaction {
        TeamRolesTable.insert {
            it[TeamRolesTable.userId] = UUID.fromString(userId)
            it[TeamRolesTable.teamId] = UUID.fromString(teamId)
            it[role] = "coach"
        }
    }

    private fun gamesSyncEnabledFlag(teamId: String): Boolean = transaction {
        TeamsTable.select(TeamsTable.gamesSyncEnabled)
            .where { TeamsTable.id eq UUID.fromString(teamId) }
            .single()[TeamsTable.gamesSyncEnabled]
    }

    private fun futureGame(gameId: Int, svTeamId: Int) = SVGame(
        gameId = gameId,
        playDateUtc = Instant.now().plus(Duration.ofDays(7)).toString(),
        teams = SVGameTeams(
            home = SVGameTeam(teamId = svTeamId, caption = "Home"),
            away = SVGameTeam(teamId = 99999, caption = "Away")
        ),
        hall = SVHall(hallId = 5, caption = "Hall", city = "Town")
    )

    @Test
    fun `coach enabling game-sync returns 200 sets flag and runs an immediate sync`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            val client = createJsonClient()
            val manager = register("gst_owner@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, teamId) = createClubAndTeam(manager.token, "GstOk")

            seedSvLink(clubId, teamId, svTeamId = 101)
            val coach = register("gst_coach@example.com")
            addCoach(teamId, coach.userId)
            stub.games = listOf(futureGame(gameId = 7001, svTeamId = 101))

            val response = client.patch("/teams/$teamId/game-sync") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(GameSyncRequest(enabled = true))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(true, response.body<GameSyncResponse>().enabled)
            assertTrue(gamesSyncEnabledFlag(teamId), "games_sync_enabled persisted as true")

            // Immediate sync ran: the canned game became an event.
            val synced = transaction {
                EventsTable.selectAll()
                    .where { (EventsTable.externalSource eq EXTERNAL_SOURCE) and (EventsTable.externalGameId eq 7001L) }
                    .count()
            }
            assertEquals(1, synced, "Toggle-on must trigger an immediate sync that imports the game")
        }
    }

    @Test
    fun `non-coach gets 403 and flag stays off`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            val client = createJsonClient()
            val manager = register("gst_403_owner@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, teamId) = createClubAndTeam(manager.token, "Gst403")
            seedSvLink(clubId, teamId, svTeamId = 202)

            // Outsider: valid JWT, no role on the team or club.
            val outsider = register("gst_403_outsider@example.com")

            val response = client.patch("/teams/$teamId/game-sync") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
                contentType(ContentType.Application.Json)
                setBody(GameSyncRequest(enabled = true))
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(!gamesSyncEnabledFlag(teamId), "Non-coach must not flip the flag")
        }
    }

    @Test
    fun `team with no SwissVolley link returns 409`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            val client = createJsonClient()
            val manager = register("gst_409_owner@example.com")
            promoteToSuperAdmin(manager.userId)
            val (_, teamId) = createClubAndTeam(manager.token, "Gst409")
            // No SV link seeded.

            val response = client.patch("/teams/$teamId/game-sync") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(GameSyncRequest(enabled = true))
            }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }
}
