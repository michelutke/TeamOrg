package ch.teamorg.routes

import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The auth rate limiter has to cover credential guessing without throttling the session
 * lookups the web app performs on every page load — those arrive server-to-server from a
 * single container IP, so limiting them would log every user out at once.
 */
class RateLimitTest : IntegrationTestBase() {

    @Test
    fun `login attempts are rate limited after the bucket is exhausted`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val statuses = (1..25).map {
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"nobody@example.com","password":"wrong-password"}""")
            }.status
        }

        assertTrue(
            statuses.any { it == HttpStatusCode.TooManyRequests },
            "expected the limiter to reject repeated login attempts, got $statuses"
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            statuses.first(),
            "the first attempt must still be processed normally"
        )
    }

    @Test
    fun `session lookups are not rate limited`() = withTeamorgTestApplication {
        val client = createJsonClient()

        // Unauthenticated, so every call is a 401 — the point is that none becomes a 429,
        // because the admin app hits this endpoint on every request.
        val statuses = (1..40).map { client.get("/auth/me").status }

        assertTrue(
            statuses.none { it == HttpStatusCode.TooManyRequests },
            "/auth/me must not be rate limited, got $statuses"
        )
    }
}
