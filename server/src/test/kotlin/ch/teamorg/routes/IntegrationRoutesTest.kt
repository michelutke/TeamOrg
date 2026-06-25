package ch.teamorg.routes

import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.infra.InvalidApiKeyException
import ch.teamorg.infra.SVGame
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntegrationRoutesTest : IntegrationTestBase() {

    /** Stub SwissVolleyClient: listTeams either succeeds or throws InvalidApiKeyException. */
    private class StubSwissVolleyClient(
        private val invalidKey: Boolean = false,
        private val teams: List<SVTeam> = emptyList()
    ) : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> {
            if (invalidKey) throw InvalidApiKeyException()
            return teams
        }

        override suspend fun listGames(apiKey: String): List<SVGame> = emptyList()
    }

    private fun koinOverride(client: SwissVolleyClient) = module {
        single<SwissVolleyClient> { client }
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val c = createJsonClient()
        return c.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    /** Creates a club + team owned by [token] (club_manager) and returns (clubId, teamId). */
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

    @Test
    fun `PUT with valid key stores integration and returns keyValid true without leaking api_key`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(invalidKey = false))) {
            val client = createJsonClient()
            val manager = register("sv_put_ok@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, _) = createClubAndTeam(manager.token, "SvPutOk")

            val response = client.put("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyKeyRequest("super-secret-key"))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val rawBody = response.bodyAsText()
            assertFalse(rawBody.contains("super-secret-key"), "Response body must NOT leak the API key")
            assertFalse(rawBody.contains("api_key"), "Response body must NOT contain api_key field")
            assertFalse(rawBody.contains("apiKey"), "Response body must NOT contain apiKey field")

            val status = response.body<SwissVolleyStatusResponse>()
            assertEquals("swissvolley", status.provider)
            assertEquals(true, status.keyValid)
            assertNull(status.syncPausedReason)
            assertNotNull(status.lastValidatedAt)
        }

    @Test
    fun `PUT with invalid key returns 422 and stores nothing`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(invalidKey = true))) {
            val client = createJsonClient()
            val manager = register("sv_put_bad@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, _) = createClubAndTeam(manager.token, "SvPutBad")

            val response = client.put("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyKeyRequest("rejected-key"))
            }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)

            // Nothing was stored: GET status returns 404.
            val getResponse = client.get("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            }
            assertEquals(HttpStatusCode.NotFound, getResponse.status)
        }

    @Test
    fun `GET status returns stored integration after PUT`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(invalidKey = false))) {
            val client = createJsonClient()
            val manager = register("sv_get@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, _) = createClubAndTeam(manager.token, "SvGet")

            client.put("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyKeyRequest("key-1"))
            }

            val response = client.get("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val status = response.body<SwissVolleyStatusResponse>()
            assertEquals("swissvolley", status.provider)
            assertEquals(true, status.keyValid)
        }

    @Test
    fun `DELETE removes key and marks links deprecated`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(invalidKey = false))) {
            val client = createJsonClient()
            val manager = register("sv_delete@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, teamId) = createClubAndTeam(manager.token, "SvDelete")

            client.put("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyKeyRequest("key-to-delete"))
            }

            // Seed a live SV link for the club's team so we can assert it gets deprecated.
            transaction {
                TeamSvLinksTable.insert {
                    it[TeamSvLinksTable.teamId] = UUID.fromString(teamId)
                    it[svTeamId] = 12345
                }
            }

            val deleteResponse = client.delete("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            }
            assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

            // Integration is gone.
            val getResponse = client.get("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            }
            assertEquals(HttpStatusCode.NotFound, getResponse.status)

            // The link is now deprecated.
            val deprecatedAt = transaction {
                TeamSvLinksTable.selectAll()
                    .where { TeamSvLinksTable.teamId eq UUID.fromString(teamId) }
                    .single()[TeamSvLinksTable.deprecatedAt]
            }
            assertNotNull(deprecatedAt, "SV link must be marked deprecated after integration delete")
        }

    @Test
    fun `non club_manager caller gets 403 on PUT GET DELETE and teams`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(invalidKey = false))) {
            val client = createJsonClient()

            // Manager owns the club.
            val manager = register("sv_idor_mgr@example.com")
            promoteToSuperAdmin(manager.userId)
            val (clubId, _) = createClubAndTeam(manager.token, "SvIdor")

            // Outsider has a valid JWT but no role in this club.
            val outsider = register("sv_idor_outsider@example.com")

            val put = client.put("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyKeyRequest("attacker-key"))
            }
            assertEquals(HttpStatusCode.Forbidden, put.status)

            val get = client.get("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
            }
            assertEquals(HttpStatusCode.Forbidden, get.status)

            val teams = client.get("/clubs/$clubId/integrations/swissvolley/teams") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
            }
            assertEquals(HttpStatusCode.Forbidden, teams.status)

            val delete = client.delete("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
            }
            assertEquals(HttpStatusCode.Forbidden, delete.status)

            // Nothing was stored by the attacker.
            val statusForManager = client.get("/clubs/$clubId/integrations/swissvolley") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            }
            assertEquals(HttpStatusCode.NotFound, statusForManager.status)
        }
}
