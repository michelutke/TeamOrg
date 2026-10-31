package ch.teamorg.routes

import ch.teamorg.db.tables.AuditLogTable
import ch.teamorg.db.tables.PasswordResetTokensTable
import ch.teamorg.mail.MailService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.dsl.module
import java.time.Instant
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeMailService : MailService {
    data class Sent(val to: String, val subject: String, val plainText: String)

    val sent = CopyOnWriteArrayList<Sent>()

    override suspend fun send(
        to: String,
        subject: String,
        plainText: String,
        html: String?,
        replyToName: String?,
        replyToEmail: String?,
        fromName: String
    ): Boolean {
        sent.add(Sent(to, subject, plainText))
        return true
    }
}

class PasswordResetTest : IntegrationTestBase() {

    private val mail = FakeMailService()
    private val fakeMailModule = module { single<MailService> { mail } }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) {
        mail.sent.clear()
        withTeamorgTestApplication(fakeMailModule, block)
    }

    private suspend fun register(client: HttpClient, email: String, password: String = "password123"): AuthResponse =
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, "Reset User"))
        }.body()

    private suspend fun forgot(client: HttpClient, email: String): HttpResponse =
        client.post("/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email))
        }

    private suspend fun reset(client: HttpClient, token: String, password: String): HttpResponse =
        client.post("/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token, password))
        }

    private suspend fun login(client: HttpClient, email: String, password: String): HttpResponse =
        client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

    /** The mail is sent asynchronously after the 204, so wait for it to arrive. */
    private suspend fun awaitMails(count: Int) {
        repeat(100) {
            if (mail.sent.size >= count) return
            delay(50)
        }
    }

    private suspend fun requestToken(client: HttpClient, email: String): String {
        val before = mail.sent.size
        assertEquals(HttpStatusCode.NoContent, forgot(client, email).status)
        awaitMails(before + 1)
        val body = mail.sent.last().plainText
        return Regex("/reset/([A-Za-z0-9_-]+)").find(body)!!.groupValues[1]
    }

    @Test
    fun `unknown email returns 204 and sends no mail`() = withApp {
        val client = createJsonClient()

        assertEquals(HttpStatusCode.NoContent, forgot(client, "nobody-reset@example.com").status)
        delay(300)

        assertTrue(mail.sent.isEmpty())
    }

    @Test
    fun `known email sends exactly one mail with a reset link`() = withApp {
        val client = createJsonClient()
        register(client, "known-reset@example.com")

        assertEquals(HttpStatusCode.NoContent, forgot(client, "known-reset@example.com").status)
        awaitMails(1)
        delay(300)

        assertEquals(1, mail.sent.size)
        assertEquals("known-reset@example.com", mail.sent.single().to)
        assertTrue(Regex("https://app\\.teamorg\\.ch/reset/[A-Za-z0-9_-]{43}").containsMatchIn(mail.sent.single().plainText))
    }

    @Test
    fun `reset sets the new password and revokes old password and old session`() = withApp {
        val client = createJsonClient()
        val auth = register(client, "flow-reset@example.com")
        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(auth.token) }.status)

        val token = requestToken(client, "flow-reset@example.com")
        assertEquals(HttpStatusCode.NoContent, reset(client, token, "newpassword456").status)

        assertEquals(HttpStatusCode.Unauthorized, login(client, "flow-reset@example.com", "password123").status)
        val newLogin = login(client, "flow-reset@example.com", "newpassword456")
        assertEquals(HttpStatusCode.OK, newLogin.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(auth.token) }.status)
        val fresh: AuthResponse = newLogin.body()
        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(fresh.token) }.status)

        val actions = transaction { AuditLogTable.selectAll().map { it[AuditLogTable.action] } }
        assertTrue("password.reset" in actions)
    }

    @Test
    fun `expired token is rejected`() = withApp {
        val client = createJsonClient()
        register(client, "expired-reset@example.com")
        val token = requestToken(client, "expired-reset@example.com")
        transaction {
            PasswordResetTokensTable.update({ PasswordResetTokensTable.usedAt.isNull() }) {
                it[expiresAt] = Instant.now().minusSeconds(60)
            }
        }

        val response = reset(client, token, "newpassword456")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(HttpStatusCode.OK, login(client, "expired-reset@example.com", "password123").status)
    }

    @Test
    fun `reused token is rejected`() = withApp {
        val client = createJsonClient()
        register(client, "reuse-reset@example.com")
        val token = requestToken(client, "reuse-reset@example.com")

        assertEquals(HttpStatusCode.NoContent, reset(client, token, "newpassword456").status)
        assertEquals(HttpStatusCode.BadRequest, reset(client, token, "anotherpass789").status)
        assertEquals(HttpStatusCode.OK, login(client, "reuse-reset@example.com", "newpassword456").status)
    }

    @Test
    fun `requesting a new token invalidates the older one`() = withApp {
        val client = createJsonClient()
        register(client, "older-reset@example.com")
        val first = requestToken(client, "older-reset@example.com")
        val second = requestToken(client, "older-reset@example.com")

        assertEquals(HttpStatusCode.BadRequest, reset(client, first, "newpassword456").status)
        assertEquals(HttpStatusCode.NoContent, reset(client, second, "newpassword456").status)
    }

    @Test
    fun `unknown token and weak password are rejected`() = withApp {
        val client = createJsonClient()
        register(client, "weak-reset@example.com")
        val token = requestToken(client, "weak-reset@example.com")

        assertEquals(HttpStatusCode.BadRequest, reset(client, "not-a-real-token", "newpassword456").status)
        assertEquals(HttpStatusCode.BadRequest, reset(client, token, "short").status)
        assertEquals(HttpStatusCode.NoContent, reset(client, token, "newpassword456").status)
    }

    private suspend fun deleteAccount(client: HttpClient, auth: AuthResponse) {
        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `soft-deleted account gets 204 and no mail`() = withApp {
        val client = createJsonClient()
        val auth = register(client, "deleted-reset@example.com")
        deleteAccount(client, auth)

        assertEquals(HttpStatusCode.NoContent, forgot(client, "deleted-reset@example.com").status)
        delay(300)

        assertTrue(mail.sent.isEmpty())
    }

    @Test
    fun `token issued before account deletion is rejected`() = withApp {
        val client = createJsonClient()
        val auth = register(client, "deleted-token-reset@example.com")
        val token = requestToken(client, "deleted-token-reset@example.com")
        deleteAccount(client, auth)

        assertEquals(HttpStatusCode.BadRequest, reset(client, token, "newpassword456").status)
    }

    @Test
    fun `jwt without token version claim works until a reset`() = withApp {
        val client = createJsonClient()
        val auth = register(client, "legacy-reset@example.com")
        val legacyToken = JWT.create()
            .withAudience("teamorg-users")
            .withIssuer("teamorg")
            .withSubject(auth.userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256("test_secret_32_chars_long_minimum_required"))
        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(legacyToken) }.status)

        val token = requestToken(client, "legacy-reset@example.com")
        assertEquals(HttpStatusCode.NoContent, reset(client, token, "newpassword456").status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(legacyToken) }.status)
    }

    @Test
    fun `parallel resets with the same token succeed exactly once`() = withApp {
        val client = createJsonClient()
        register(client, "parallel-reset@example.com")
        val token = requestToken(client, "parallel-reset@example.com")

        val statuses = coroutineScope {
            listOf(
                async { reset(client, token, "newpassword456").status },
                async { reset(client, token, "otherpassword789").status }
            ).awaitAll()
        }

        assertEquals(1, statuses.count { it == HttpStatusCode.NoContent }, "got $statuses")
        assertEquals(1, statuses.count { it == HttpStatusCode.BadRequest }, "got $statuses")
    }

    @Test
    fun `forgot-password traffic does not exhaust the login bucket`() = withApp {
        val client = createJsonClient()
        register(client, "bucket-reset@example.com")

        repeat(25) { forgot(client, "bucket-reset@example.com") }

        assertEquals(HttpStatusCode.OK, login(client, "bucket-reset@example.com", "password123").status)
    }

    @Test
    fun `forgot-password is rate limited`() = withApp {
        val client = createJsonClient()

        val statuses = (1..25).map { forgot(client, "limited-reset@example.com").status }

        assertEquals(HttpStatusCode.NoContent, statuses.first())
        assertTrue(statuses.any { it == HttpStatusCode.TooManyRequests }, "got $statuses")
    }
}
