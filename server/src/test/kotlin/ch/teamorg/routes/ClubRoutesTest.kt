package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClubRoutesTest : IntegrationTestBase() {

    @Test
    fun `create club success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("club@example.com", "password123", "Club Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val response = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Volley Masters", "volleyball", "Zurich"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val club = response.body<Club>()
        assertEquals("Volley Masters", club.name)
        assertEquals("Zurich", club.location)
    }

    @Test
    fun `get club returns correct data`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("getclub@example.com", "password123", "Club Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Get Club Inc", "volleyball", "Bern"))
        }.body<Club>().id

        val response = client.get("/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val club = response.body<Club>()
        assertEquals("Get Club Inc", club.name)
    }

    @Test
    fun `update club name success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("updclub@example.com", "password123", "Club Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Old Name", "volleyball", "Geneva"))
        }.body<Club>().id

        val response = client.patch("/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateClubRequest(name = "New Name"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val club = response.body<Club>()
        assertEquals("New Name", club.name)
        assertEquals("Geneva", club.location)
    }

    @Test
    fun `upload logo stored successfully`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("logoclub@example.com", "password123", "Club Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Logo Club", "volleyball", "Lucerne"))
        }.body<Club>().id

        val response = client.post("/clubs/$clubId/logo") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            setBody(MultiPartFormDataContent(
                formData {
                    append("logo", "fake-image-content".toByteArray(), Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=\"logo.png\"")
                    })
                }
            ))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val club = response.body<Club>()
        assertNotNull(club.logoUrl)
    }

    @Test
    fun `create club without auth returns 401`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val response = client.post("/clubs") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Unauthorized Club"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `get club without auth returns 401`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("noauthget@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Auth Required Club"))
        }.body<Club>().id

        val response = client.get("/clubs/$clubId")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `update club as non-manager returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("manager403@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Protected Club"))
        }.body<Club>().id

        val otherAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("nonmanager403@example.com", "password123", "Non Manager"))
        }.body<AuthResponse>()

        val response = client.patch("/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${otherAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateClubRequest(name = "Hijacked Name"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create club with blank name returns 400`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("blankclub@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val response = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get nonexistent club returns 404`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("get404@example.com", "password123", "Creator"))
        }.body<AuthResponse>()

        val response = client.get("/clubs/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `create team in club as non-manager returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teammgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Managed Club", "volleyball", "Zurich"))
        }.body<Club>().id

        val otherAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("nonmgr@example.com", "password123", "Non Manager"))
        }.body<AuthResponse>()

        val response = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${otherAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Unauthorized Team"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `list teams in club`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("listteams@example.com", "password123", "Club Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Multi-Team Club", "volleyball", "Basel"))
        }.body<Club>().id

        client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Team A"))
        }

        client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Team B"))
        }

        val response = client.get("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val teams = response.body<List<Team>>()
        assertEquals(2, teams.size)
    }
}
