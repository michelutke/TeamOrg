package ch.teamorg.routes

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountDeletionGuardTest : IntegrationTestBase() {

    /** Marks a user deleted the way the repository will in Task 2, without depending on it. */
    private fun markDeleted(userId: String) {
        transaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[deletedAt] = Instant.now()
                it[email] = "deleted-$userId@deleted.invalid"
                it[displayName] = "Gelöschtes Konto"
                it[passwordHash] = "!"
            }
        }
    }

    @Test
    fun `token of a deleted user is rejected on a route that does not use authenticateUser`() =
        withTeamorgTestApplication {
            val client = createJsonClient()
            val auth = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("guard1@example.com", "password123", "Guard One"))
            }.body<AuthResponse>()

            // Sanity: the token works before deletion. /users/me/abwesenheit is in
            // AbwesenheitRoutes, which reads the principal directly.
            val before = client.get("/users/me/abwesenheit") { bearerAuth(auth.token) }
            assertEquals(HttpStatusCode.OK, before.status)

            markDeleted(auth.userId)

            val after = client.get("/users/me/abwesenheit") { bearerAuth(auth.token) }
            assertEquals(HttpStatusCode.Unauthorized, after.status)
        }

    @Test
    fun `token of a deleted user is rejected on auth me`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard2@example.com", "password123", "Guard Two"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with the original email of a deleted user fails`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard3@example.com", "password123", "Guard Three"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("guard3@example.com", "password123"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `the email of a deleted user can be registered again`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard4@example.com", "password123", "Guard Four"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard4@example.com", "newpassword123", "Guard Four Again"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val fresh = response.body<AuthResponse>()
        assertEquals("Guard Four Again", fresh.displayName)
        // A genuinely new row, not the scrubbed one revived.
        assert(fresh.userId != auth.userId)
    }
}
