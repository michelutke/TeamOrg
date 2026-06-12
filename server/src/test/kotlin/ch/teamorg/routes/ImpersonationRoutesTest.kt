package ch.teamorg.routes

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImpersonationRoutesTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.registerSuperAdmin(
        client: HttpClient,
        email: String = "impadmin@test.com"
    ): AuthResponse {
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "Impersonation Admin"))
        }.body<AuthResponse>()

        transaction {
            UsersTable.update({ UsersTable.id eq java.util.UUID.fromString(auth.userId) }) {
                it[isSuperAdmin] = true
            }
        }
        return auth
    }

    @Test
    fun `superadmin can start impersonation`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val adminAuth = registerSuperAdmin(client, "imp-start-sa@test.com")

        val targetAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("imp-target@test.com", "password123", "Target User"))
        }.body<AuthResponse>()

        val response = client.post("/admin/impersonate/start") {
            header(HttpHeaders.Authorization, "Bearer ${adminAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(StartImpersonationRequest(targetAuth.userId))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val impResponse = response.body<ImpersonationResponse>()
        assertNotNull(impResponse.token)
        assertNotNull(impResponse.sessionId)
        assertEquals(targetAuth.userId, impResponse.targetUser.id)
    }

    @Test
    fun `cannot impersonate another superadmin`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val admin1Auth = registerSuperAdmin(client, "imp-sa1@test.com")
        val admin2Auth = registerSuperAdmin(client, "imp-sa2@test.com")

        val response = client.post("/admin/impersonate/start") {
            header(HttpHeaders.Authorization, "Bearer ${admin1Auth.token}")
            contentType(ContentType.Application.Json)
            setBody(StartImpersonationRequest(admin2Auth.userId))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `impersonation status reports active when using impersonation token`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val adminAuth = registerSuperAdmin(client, "imp-status-sa@test.com")

        val targetAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("imp-status-target@test.com", "password123", "Status Target"))
        }.body<AuthResponse>()

        val impToken = client.post("/admin/impersonate/start") {
            header(HttpHeaders.Authorization, "Bearer ${adminAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(StartImpersonationRequest(targetAuth.userId))
        }.body<ImpersonationResponse>().token

        val response = client.get("/admin/impersonate/status") {
            header(HttpHeaders.Authorization, "Bearer $impToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val status = response.body<ImpersonationStatusResponse>()
        assertTrue(status.impersonating)
        assertNotNull(status.remainingSeconds)
    }

    @Test
    fun `can end impersonation session`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val adminAuth = registerSuperAdmin(client, "imp-end-sa@test.com")

        val targetAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("imp-end-target@test.com", "password123", "End Target"))
        }.body<AuthResponse>()

        val impToken = client.post("/admin/impersonate/start") {
            header(HttpHeaders.Authorization, "Bearer ${adminAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(StartImpersonationRequest(targetAuth.userId))
        }.body<ImpersonationResponse>().token

        val response = client.post("/admin/impersonate/end") {
            header(HttpHeaders.Authorization, "Bearer $impToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `normal token reports not impersonating`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val adminAuth = registerSuperAdmin(client, "imp-normal-sa@test.com")

        val response = client.get("/admin/impersonate/status") {
            header(HttpHeaders.Authorization, "Bearer ${adminAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val status = response.body<ImpersonationStatusResponse>()
        assertTrue(!status.impersonating)
    }
}
