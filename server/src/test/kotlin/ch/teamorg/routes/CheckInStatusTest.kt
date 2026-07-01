package ch.teamorg.routes

import ch.teamorg.db.tables.EventsTable
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.EventWithTeams
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class CreateEventPayloadCIS(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val teamIds: List<String> = emptyList()
)

class CheckInStatusTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.registerAndLogin(
        email: String,
        password: String = "Password1!",
        displayName: String = "User"
    ): AuthResponse {
        val client = createJsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, displayName))
        }
        return client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<AuthResponse>()
    }

    private suspend fun ApplicationTestBuilder.createEvent(
        token: String,
        startAt: String,
        endAt: String,
        title: String = "Test Event"
    ): Event {
        val client = createJsonClient()
        return client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadCIS(
                title = title,
                type = "training",
                startAt = startAt,
                endAt = endAt
            ))
        }.body<Event>()
    }

    @Test
    fun `past event with null check_in_completed_at returns awaiting_checkin`() =
        withTeamorgTestApplication {
            val auth = registerAndLogin("cis_past@example.com", displayName = "CIS Past")
            val event = createEvent(
                token = auth.token,
                startAt = "2020-01-01T10:00:00Z",
                endAt = "2020-01-01T12:00:00Z",
                title = "Past CIS Event"
            )

            val client = createJsonClient()
            val fetched = client.get("/events/${event.id}") {
                header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            }.body<EventWithTeams>()

            assertEquals("awaiting_checkin", fetched.event.checkInStatus)
        }

    @Test
    fun `future event with null check_in_completed_at returns open`() =
        withTeamorgTestApplication {
            val auth = registerAndLogin("cis_future@example.com", displayName = "CIS Future")
            val event = createEvent(
                token = auth.token,
                startAt = "2099-01-01T10:00:00Z",
                endAt = "2099-01-01T12:00:00Z",
                title = "Future CIS Event"
            )

            val client = createJsonClient()
            val fetched = client.get("/events/${event.id}") {
                header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            }.body<EventWithTeams>()

            assertEquals("open", fetched.event.checkInStatus)
        }
}
