package ch.teamorg.routes

import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.AuditLogTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.ImpersonationSessionsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionTest : IntegrationTestBase() {

    private suspend fun register(
        client: io.ktor.client.HttpClient,
        email: String,
        password: String = "password123",
        name: String = "Test User"
    ): AuthResponse = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, password, name))
    }.body()

    @Test
    fun `wrong password returns 401 and leaves the account working`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del1@example.com")

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("wrong-password"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val stillWorks = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, stillWorks.status)
    }

    @Test
    fun `deletion scrubs the user row`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del2@example.com", name = "Delete Me")

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        transaction {
            val row = UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(auth.userId) }
                .single()
            assertNotNull(row[UsersTable.deletedAt])
            assertEquals("deleted-${auth.userId}@deleted.invalid", row[UsersTable.email])
            assertEquals("Gelöschtes Konto", row[UsersTable.displayName])
            assertNull(row[UsersTable.avatarUrl])
            assertEquals("!", row[UsersTable.passwordHash])
        }
    }

    @Test
    fun `deletion removes personal rows`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del3@example.com")

        // Give the user an absence rule so there is something personal to delete.
        val ruleResponse = client.post("/users/me/abwesenheit") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonBody(
                    """{"presetType":"holidays","label":"Ferien","ruleType":"period","startDate":"2026-09-01","endDate":"2026-09-14"}"""
                )
            )
        }
        assertTrue(ruleResponse.status.isSuccess(), "absence rule setup failed: ${ruleResponse.bodyAsText()}")

        client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }

        transaction {
            val userId = UUID.fromString(auth.userId)
            assertTrue(
                AbwesenheitRulesTable.selectAll().where { AbwesenheitRulesTable.userId eq userId }.empty(),
                "absence rules survived deletion"
            )
            assertTrue(
                TeamRolesTable.selectAll().where { TeamRolesTable.userId eq userId }.empty(),
                "team roles survived deletion"
            )
        }
    }

    @Test
    fun `deletion writes an audit log entry`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del4@example.com")

        client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }

        transaction {
            val entries = AuditLogTable.selectAll()
                .where { AuditLogTable.actorId eq UUID.fromString(auth.userId) }
                .map { it[AuditLogTable.action] }
            assertTrue("user.self_delete" in entries, "expected a user.self_delete audit entry, got $entries")
        }
    }

    @Test
    fun `deletion revokes impersonation sessions involving the user`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val admin = register(client, "imp-admin@example.com", name = "Admin")
        val auth = register(client, "del5@example.com")

        val asTarget = UUID.randomUUID()
        val asActor = UUID.randomUUID()
        transaction {
            ImpersonationSessionsTable.insert {
                it[id] = asTarget
                it[actorId] = UUID.fromString(admin.userId)
                it[targetId] = UUID.fromString(auth.userId)
                it[expiresAt] = Instant.now().plusSeconds(3600)
            }
            ImpersonationSessionsTable.insert {
                it[id] = asActor
                it[actorId] = UUID.fromString(auth.userId)
                it[targetId] = UUID.fromString(admin.userId)
                it[expiresAt] = Instant.now().plusSeconds(3600)
            }
        }

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        transaction {
            val rows = ImpersonationSessionsTable.selectAll()
                .where { (ImpersonationSessionsTable.id eq asTarget) or (ImpersonationSessionsTable.id eq asActor) }
                .toList()
            assertEquals(2, rows.size, "impersonation session rows must survive deletion")
            rows.forEach { row ->
                assertEquals(false, row[ImpersonationSessionsTable.isActive], "session still active after deletion")
                assertNotNull(row[ImpersonationSessionsTable.endedAt], "session has no ended_at after deletion")
            }
        }
    }

    @Test
    fun `a club owner is blocked with 409 and nothing is deleted`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "owner@example.com", name = "Club Owner")

        // The self-serve creation route requires a live Stripe backend, so set
        // clubs.owner_user_id directly — that is the only precondition that matters.
        transaction {
            ClubsTable.insert {
                it[name] = "Owner Club"
                it[ownerUserId] = UUID.fromString(auth.userId)
            }
        }

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("owns_clubs", body["reason"]?.jsonPrimitive?.content)
        assertEquals("Owner Club", body["clubs"]?.jsonArray?.first()?.jsonPrimitive?.content)

        // Nothing may have been deleted or scrubbed by a rejected request.
        transaction {
            val row = UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(auth.userId) }
                .single()
            assertNull(row[UsersTable.deletedAt])
            assertEquals("owner@example.com", row[UsersTable.email])
        }
        val stillWorks = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, stillWorks.status)
    }
}

private fun buildJsonBody(json: String) = io.ktor.http.content.TextContent(json, ContentType.Application.Json)
