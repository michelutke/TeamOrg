package ch.teamorg.routes

import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.infra.InvalidApiKeyException
import ch.teamorg.infra.SVGame
import ch.teamorg.infra.SVLeague
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationImportTest : IntegrationTestBase() {

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

    /** Creates a club owned by [token] (club_manager) and returns its id. */
    private suspend fun ApplicationTestBuilder.createClub(token: String, name: String): String {
        val c = createJsonClient()
        return c.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("$name Club"))
        }.body<Club>().id
    }

    /** Stores a valid SwissVolley API key for [clubId] via the PUT route. */
    private suspend fun ApplicationTestBuilder.storeValidKey(clubId: String, token: String) {
        createJsonClient().put("/clubs/$clubId/integrations/swissvolley") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SwissVolleyKeyRequest("valid-key"))
        }
    }

    private val sampleTeams = listOf(
        SVTeam(
            teamId = 101,
            seasonalTeamId = 9101,
            caption = "Damen 1",
            gender = "f",
            league = SVLeague(leagueId = 1, caption = "NLA")
        ),
        SVTeam(
            teamId = 202,
            seasonalTeamId = 9202,
            caption = "Herren 1",
            gender = "m",
            league = SVLeague(leagueId = 2, caption = "NLB")
        )
    )

    @Test
    fun `club_manager imports two svTeamIds creating two teams and two links with correct fields`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(teams = sampleTeams))) {
            val client = createJsonClient()
            val manager = register("import_ok@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "ImportOk")
            storeValidKey(clubId, manager.token)

            val response = client.post("/clubs/$clubId/teams/import") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyImportRequest(listOf(101, 202)))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<SwissVolleyImportResponse>()
            assertEquals(2, result.created.size)
            assertTrue(result.skipped.isEmpty())
            assertEquals(setOf(101, 202), result.created.map { it.svTeamId }.toSet())

            val createdTeamIds = result.created.map { UUID.fromString(it.teamId) }.toSet()

            val links = transaction {
                TeamSvLinksTable.selectAll()
                    .where { TeamSvLinksTable.teamId inList createdTeamIds }
                    .map {
                        Triple(
                            it[TeamSvLinksTable.svTeamId],
                            it[TeamSvLinksTable.svLeagueCaption],
                            it[TeamSvLinksTable.svGender]
                        )
                    }
            }
            assertEquals(2, links.size)
            assertEquals(
                setOf(
                    Triple(101, "NLA", "f"),
                    Triple(202, "NLB", "m")
                ),
                links.toSet()
            )

            // Caption became the team name.
            val names = result.created.associate { it.svTeamId to it.name }
            assertEquals("Damen 1", names[101])
            assertEquals("Herren 1", names[202])
        }

    @Test
    fun `re-importing an already-linked svTeamId is skipped and not duplicated`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(teams = sampleTeams))) {
            val client = createJsonClient()
            val manager = register("import_dedupe@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "ImportDedupe")
            storeValidKey(clubId, manager.token)

            val first = client.post("/clubs/$clubId/teams/import") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyImportRequest(listOf(101)))
            }.body<SwissVolleyImportResponse>()
            assertEquals(1, first.created.size)
            val firstTeamId = UUID.fromString(first.created.single().teamId)

            val second = client.post("/clubs/$clubId/teams/import") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyImportRequest(listOf(101, 202)))
            }
            assertEquals(HttpStatusCode.OK, second.status)
            val secondBody = second.body<SwissVolleyImportResponse>()

            assertEquals(listOf(202), secondBody.created.map { it.svTeamId })
            assertEquals(listOf(101), secondBody.skipped)

            // Exactly one link for the team created by the first import — no duplicate,
            // and re-importing did not create a second team for sv_team_id 101.
            val linksForFirstTeam = transaction {
                TeamSvLinksTable.selectAll()
                    .where { TeamSvLinksTable.teamId eq firstTeamId }
                    .map { it[TeamSvLinksTable.svTeamId] }
            }
            assertEquals(listOf(101), linksForFirstTeam)
        }

    @Test
    fun `import with no valid stored key returns 409`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(teams = sampleTeams))) {
            val client = createJsonClient()
            val manager = register("import_nokey@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "ImportNoKey")
            // No key stored.

            // DB (team_sv_links) is shared across tests in this class — capture baseline.
            val before = transaction { TeamSvLinksTable.selectAll().count() }

            val response = client.post("/clubs/$clubId/teams/import") {
                header(HttpHeaders.Authorization, "Bearer ${manager.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyImportRequest(listOf(101)))
            }

            assertEquals(HttpStatusCode.Conflict, response.status)

            // Nothing imported.
            val after = transaction { TeamSvLinksTable.selectAll().count() }
            assertEquals(before, after)
        }

    @Test
    fun `non club_manager caller gets 403 on import`() =
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient(teams = sampleTeams))) {
            val client = createJsonClient()

            // Manager owns the club and has a valid key stored.
            val manager = register("import_idor_mgr@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "ImportIdor")
            storeValidKey(clubId, manager.token)

            // Outsider has a valid JWT but no role in this club.
            val outsider = register("import_idor_outsider@example.com")

            // DB (team_sv_links) is shared across tests in this class — capture baseline.
            val before = transaction { TeamSvLinksTable.selectAll().count() }

            val response = client.post("/clubs/$clubId/teams/import") {
                header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
                contentType(ContentType.Application.Json)
                setBody(SwissVolleyImportRequest(listOf(101, 202)))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)

            // Attacker imported nothing.
            val after = transaction { TeamSvLinksTable.selectAll().count() }
            assertEquals(before, after)
        }
}
