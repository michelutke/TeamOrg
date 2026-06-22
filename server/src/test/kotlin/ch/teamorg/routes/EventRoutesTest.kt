package ch.teamorg.routes

import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.EventWithTeams
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Serializable
private data class CreateEventPayload(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val meetupAt: String? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val teamIds: List<String> = emptyList(),
    val subgroupIds: List<String> = emptyList()
)

@Serializable
private data class EditEventPayload(
    val scope: String? = "this_only",
    val title: String? = null,
    val type: String? = null
)

@Serializable
private data class CancelPayload(val scope: String? = "this_only")

class EventRoutesTest : IntegrationTestBase() {

    private fun authToken(client: io.ktor.client.HttpClient, email: String) =
        withTeamorgTestApplication {
            // Used inline — see test bodies
        }

    @Test
    fun `POST events with valid one-off event returns 201 with event JSON`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_create@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val response = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Team Training",
                type = "training",
                startAt = "2026-04-01T10:00:00Z",
                endAt = "2026-04-01T12:00:00Z"
            ))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val event = response.body<Event>()
        assertNotNull(event.id)
        assertEquals("Team Training", event.title)
        assertEquals("training", event.type)
    }

    @Test
    fun `POST events recurring with omitted weekdays and intervalDays returns 201`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_recurring@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        // Mirrors exactly what the app sends for a weekly series: the recurring object
        // omits weekdays and intervalDays (kotlinx drops nulls). The server's
        // RecurringPattern must default these or call.receive throws -> 500 -> app crash.
        val rawBody = """
            {
              "title": "Weekly Training",
              "type": "training",
              "startAt": "2026-04-01T10:00:00Z",
              "endAt": "2026-04-01T12:00:00Z",
              "recurring": { "patternType": "weekly", "seriesEndDate": "2026-06-01" }
            }
        """.trimIndent()

        val response = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(rawBody)
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `GET users me events returns list of events`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_list@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Training Session",
                type = "training",
                startAt = "2026-04-02T09:00:00Z",
                endAt = "2026-04-02T11:00:00Z"
            ))
        }

        val response = client.get("/users/me/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val events = response.body<List<EventWithTeams>>()
        assertNotNull(events)
    }

    @Test
    fun `GET users me events filters by type parameter`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_filter@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Match Day",
                type = "match",
                startAt = "2026-04-03T14:00:00Z",
                endAt = "2026-04-03T16:00:00Z"
            ))
        }

        client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Training Day",
                type = "training",
                startAt = "2026-04-04T14:00:00Z",
                endAt = "2026-04-04T16:00:00Z"
            ))
        }

        val response = client.get("/users/me/events?type=training") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val events = response.body<List<EventWithTeams>>()
        events.forEach { assertEquals("training", it.event.type) }
    }

    @Test
    fun `GET events id returns event detail with matchedTeams`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_detail@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Detail Event",
                type = "training",
                startAt = "2026-04-05T10:00:00Z",
                endAt = "2026-04-05T12:00:00Z"
            ))
        }.body<Event>()

        val response = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val eventWithTeams = response.body<EventWithTeams>()
        assertEquals("Detail Event", eventWithTeams.event.title)
        assertNotNull(eventWithTeams.matchedTeams)
    }

    @Test
    fun `PATCH events id with scope this_only updates single event`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_patch@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Original Title",
                type = "training",
                startAt = "2026-04-06T10:00:00Z",
                endAt = "2026-04-06T12:00:00Z"
            ))
        }.body<Event>()

        val patchResponse = client.patch("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(EditEventPayload(scope = "this_only", title = "Updated Title"))
        }

        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val updated = patchResponse.body<EventWithTeams>()
        assertEquals("Updated Title", updated.event.title)
    }

    @Test
    fun `POST events id cancel sets status to cancelled`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_cancel@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "To Cancel",
                type = "training",
                startAt = "2026-04-07T10:00:00Z",
                endAt = "2026-04-07T12:00:00Z"
            ))
        }.body<Event>()

        val cancelResponse = client.post("/events/${event.id}/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CancelPayload(scope = "this_only"))
        }

        assertEquals(HttpStatusCode.OK, cancelResponse.status)

        val checkResponse = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        val cancelled = checkResponse.body<EventWithTeams>()
        assertEquals("cancelled", cancelled.event.status)
    }

    @Test
    fun `POST events id duplicate returns new event with different id but same title`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_dup@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Original",
                type = "training",
                startAt = "2026-04-08T10:00:00Z",
                endAt = "2026-04-08T12:00:00Z"
            ))
        }.body<Event>()

        val dupResponse = client.post("/events/${event.id}/duplicate") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.Created, dupResponse.status)
        val duplicate = dupResponse.body<Event>()
        assertNotEquals(event.id, duplicate.id)
        assertEquals("Original", duplicate.title)
    }

    @Test
    fun `GET events nonexistent returns 404`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_404@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val response = client.get("/events/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET teams teamId events returns team event list`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("event_team@example.com", "password123", "EventUser"))
        }.body<AuthResponse>()

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Event Club"))
        }.body<ch.teamorg.domain.models.Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Event Team"))
        }.body<ch.teamorg.domain.models.Team>().id

        val response = client.get("/teams/$teamId/events") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val events = response.body<List<Event>>()
        assertNotNull(events)
    }

    @Test
    fun `club manager sees events via GET users me events for teams they manage`() = withTeamorgTestApplication {
        val client = createJsonClient()

        // Register club manager and create club + team
        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mgr_event@example.com", "password123", "Manager"))
        }.body<AuthResponse>()

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Event Mgr Club"))
        }.body<ch.teamorg.domain.models.Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Event Mgr Team"))
        }.body<ch.teamorg.domain.models.Team>().id

        // Manager creates event targeting the team
        val createResponse = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload(
                title = "Manager Training",
                type = "training",
                startAt = "2026-04-10T10:00:00Z",
                endAt = "2026-04-10T12:00:00Z",
                teamIds = listOf(teamId)
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        // Manager (who has NO team_role, only club_role) should still see the event
        val listResponse = client.get("/users/me/events") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val events = listResponse.body<List<EventWithTeams>>()
        assertEquals(1, events.size)
        assertEquals("Manager Training", events[0].event.title)
    }
}
