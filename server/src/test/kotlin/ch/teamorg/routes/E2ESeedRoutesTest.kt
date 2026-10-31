package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class E2ESeedRoutesTest : IntegrationTestBase() {

    @Test
    fun `seed club route is 404 when E2E seed is not enabled`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val response = client.post("/e2e/seed/club") {
            contentType(ContentType.Application.Json)
            setBody(E2ESeedClubRequest(name = "Seed Club", managerEmail = "nobody@e2e.test"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `seed club route is 404 when E2E seed is explicitly false`() =
        withTeamorgTestApplication(extraConfig = mapOf("e2e.seed-enabled" to "false")) {
            val client = createJsonClient()
            val response = client.post("/e2e/seed/club") {
                contentType(ContentType.Application.Json)
                setBody(E2ESeedClubRequest(name = "Seed Club", managerEmail = "nobody@e2e.test"))
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `seed club route creates club with registered user as manager when enabled`() =
        withTeamorgTestApplication(extraConfig = mapOf("e2e.seed-enabled" to "true")) {
            val client = createJsonClient()
            val email = "seed-manager-${System.nanoTime()}@e2e.test"
            val auth = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, "Password1!", "Seed Manager"))
            }.body<AuthResponse>()

            val response = client.post("/e2e/seed/club") {
                contentType(ContentType.Application.Json)
                setBody(E2ESeedClubRequest(name = "Seed Club", managerEmail = email))
            }
            assertEquals(HttpStatusCode.Created, response.status)
            val club = response.body<Club>()

            val teams = client.post("/clubs/${club.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest("Seed Team"))
            }
            assertEquals(HttpStatusCode.Created, teams.status)
        }

    @Test
    fun `seed club route returns 404 for unknown manager email when enabled`() =
        withTeamorgTestApplication(extraConfig = mapOf("e2e.seed-enabled" to "true")) {
            val client = createJsonClient()
            val response = client.post("/e2e/seed/club") {
                contentType(ContentType.Application.Json)
                setBody(E2ESeedClubRequest(name = "Seed Club", managerEmail = "ghost-${System.nanoTime()}@e2e.test"))
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
