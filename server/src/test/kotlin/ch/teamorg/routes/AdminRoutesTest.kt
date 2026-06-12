package ch.teamorg.routes

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.repositories.AuditLogPage
import ch.teamorg.domain.repositories.ClubDetail
import ch.teamorg.domain.repositories.ClubListPage
import ch.teamorg.domain.repositories.DashboardStats
import ch.teamorg.domain.repositories.UserSearchPage
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminRoutesTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.registerSuperAdmin(
        client: HttpClient,
        email: String = "superadmin@test.com"
    ): AuthResponse {
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "Super Admin"))
        }.body<AuthResponse>()

        transaction {
            UsersTable.update({ UsersTable.id eq java.util.UUID.fromString(auth.userId) }) {
                it[isSuperAdmin] = true
            }
        }
        return auth
    }

    @Test
    fun `non-superadmin gets 403 on admin endpoints`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("normaluser@test.com", "password123", "Normal User"))
        }.body<AuthResponse>()

        val response = client.get("/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `superadmin can get dashboard stats`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-stats@test.com")

        val response = client.get("/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val stats = response.body<DashboardStats>()
        assertNotNull(stats.totalClubs)
        assertNotNull(stats.totalUsers)
    }

    @Test
    fun `superadmin can create and list clubs`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-clubs@test.com")

        val createResponse = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Admin Test Club", "volleyball", "Bern"))
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<JsonObject>()
        assertEquals("Admin Test Club", created["name"]?.jsonPrimitive?.content)

        val listResponse = client.get("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val page = listResponse.body<ClubListPage>()
        assertTrue(page.clubs.any { it.name == "Admin Test Club" })
    }

    @Test
    fun `superadmin can get club detail with managers array`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-detail@test.com")

        val clubId = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Detail Club", "volleyball", "Zurich"))
        }.body<JsonObject>()["id"]?.jsonPrimitive?.content

        assertNotNull(clubId)

        val response = client.get("/admin/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val detail = response.body<ClubDetail>()
        assertEquals("Detail Club", detail.name)
        assertNotNull(detail.managers)
    }

    @Test
    fun `superadmin can update club name`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-update@test.com")

        val clubId = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Old Club Name", "volleyball", "Geneva"))
        }.body<JsonObject>()["id"]?.jsonPrimitive?.content

        assertNotNull(clubId)

        val patchResponse = client.patch("/admin/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateClubAdminRequest(name = "New Club Name"))
        }

        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val updated = patchResponse.body<ClubDetail>()
        assertEquals("New Club Name", updated.name)
    }

    @Test
    fun `superadmin can deactivate and reactivate club`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-lifecycle@test.com")

        val clubId = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Lifecycle Club", "volleyball", "Lausanne"))
        }.body<JsonObject>()["id"]?.jsonPrimitive?.content

        assertNotNull(clubId)

        val deactivateResponse = client.post("/admin/clubs/$clubId/deactivate") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals(HttpStatusCode.OK, deactivateResponse.status)
        val deactivated = deactivateResponse.body<JsonObject>()
        assertEquals("deactivated", deactivated["status"]?.jsonPrimitive?.content)

        val reactivateResponse = client.post("/admin/clubs/$clubId/reactivate") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals(HttpStatusCode.OK, reactivateResponse.status)
        val reactivated = reactivateResponse.body<JsonObject>()
        assertEquals("active", reactivated["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `superadmin can delete club`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-delete@test.com")

        val clubId = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Delete Me Club", "volleyball", "Basel"))
        }.body<JsonObject>()["id"]?.jsonPrimitive?.content

        assertNotNull(clubId)

        val deleteResponse = client.delete("/admin/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        val result = deleteResponse.body<JsonObject>()
        assertEquals("deleted", result["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `superadmin can manage club managers`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-mgr@test.com")

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("clubmanager@test.com", "password123", "Club Manager"))
        }.body<AuthResponse>()

        val clubId = client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Manager Club", "volleyball", "St. Gallen"))
        }.body<JsonObject>()["id"]?.jsonPrimitive?.content

        assertNotNull(clubId)

        val addResponse = client.post("/admin/clubs/$clubId/managers") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AddManagerRequest("clubmanager@test.com"))
        }
        assertEquals(HttpStatusCode.Created, addResponse.status)

        val detailAfterAdd = client.get("/admin/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }.body<ClubDetail>()
        assertTrue(detailAfterAdd.managers.any { it.email == "clubmanager@test.com" })

        val removeResponse = client.delete("/admin/clubs/$clubId/managers/${managerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals(HttpStatusCode.OK, removeResponse.status)

        val detailAfterRemove = client.get("/admin/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }.body<ClubDetail>()
        assertTrue(detailAfterRemove.managers.none { it.email == "clubmanager@test.com" })
    }

    @Test
    fun `superadmin can search users`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-search@test.com")

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("searchable-user@test.com", "password123", "Searchable User"))
        }

        val response = client.get("/admin/users?q=searchable") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<UserSearchPage>()
        assertTrue(page.users.any { it.email == "searchable-user@test.com" })
    }

    @Test
    fun `audit log captures admin actions`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerSuperAdmin(client, "sa-audit@test.com")

        client.post("/admin/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubAdminRequest("Audit Club", "volleyball", "Winterthur"))
        }

        val response = client.get("/admin/audit-log") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val log = response.body<AuditLogPage>()
        assertTrue(log.entries.isNotEmpty())
        assertTrue(log.entries.any { it.action == "club.create" })
    }
}
