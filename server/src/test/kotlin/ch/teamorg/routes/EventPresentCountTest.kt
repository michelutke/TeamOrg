package ch.teamorg.routes

import ch.teamorg.db.tables.AttendanceRecordsTable
import ch.teamorg.db.tables.RecordStatus
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.EventWithTeams
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class CreateEventPayloadEPC(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val teamIds: List<String> = emptyList()
)

class EventPresentCountTest : IntegrationTestBase() {

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
        title: String
    ): Event {
        val client = createJsonClient()
        return client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadEPC(
                title = title,
                type = "training",
                startAt = "2026-10-01T10:00:00Z",
                endAt = "2026-10-01T12:00:00Z"
            ))
        }.body<Event>()
    }

    @Test
    fun `event payload carries presentCount`() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("epc_coach@example.com", displayName = "Coach EPC")
        val playerAuth = registerAndLogin("epc_player@example.com", displayName = "Player EPC")

        val event = createEvent(coachAuth.token, "Event EPC Present")
        val eventId = event.id
        val coachId = UUID.fromString(coachAuth.userId)
        val playerId = UUID.fromString(playerAuth.userId)
        val now = Instant.now()

        // Insert one present + one absent record directly
        transaction {
            AttendanceRecordsTable.insert { row ->
                row[AttendanceRecordsTable.eventId] = eventId
                row[AttendanceRecordsTable.userId] = coachId
                row[AttendanceRecordsTable.status] = RecordStatus.present
                row[AttendanceRecordsTable.setBy] = coachId
                row[AttendanceRecordsTable.setAt] = now
            }
            AttendanceRecordsTable.insert { row ->
                row[AttendanceRecordsTable.eventId] = eventId
                row[AttendanceRecordsTable.userId] = playerId
                row[AttendanceRecordsTable.status] = RecordStatus.absent
                row[AttendanceRecordsTable.setBy] = coachId
                row[AttendanceRecordsTable.setAt] = now
            }
        }

        val client = createJsonClient()
        val result = client.get("/events/$eventId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()

        assertEquals(1, result.event.presentCount)
    }

    @Test
    fun `event payload has presentCount zero when no records`() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("epc_zero_coach@example.com", displayName = "Coach EPC Zero")

        val event = createEvent(coachAuth.token, "Event EPC Zero")
        val eventId = event.id

        val client = createJsonClient()
        val result = client.get("/events/$eventId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()

        assertEquals(0, result.event.presentCount)
    }

    @Test
    fun `users me events list carries presentCount`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = registerAndLogin("epc_list_mgr@example.com", displayName = "Manager EPC List")
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("EPC List Club"))
        }.body<ch.teamorg.domain.models.Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("EPC List Team"))
        }.body<ch.teamorg.domain.models.Team>().id

        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadEPC(
                title = "EPC List Event",
                type = "training",
                startAt = "2026-11-01T10:00:00Z",
                endAt = "2026-11-01T12:00:00Z",
                teamIds = listOf(teamId)
            ))
        }.body<Event>()

        val managerId = UUID.fromString(managerAuth.userId)
        val now = Instant.now()

        transaction {
            AttendanceRecordsTable.insert { row ->
                row[AttendanceRecordsTable.eventId] = event.id
                row[AttendanceRecordsTable.userId] = managerId
                row[AttendanceRecordsTable.status] = RecordStatus.present
                row[AttendanceRecordsTable.setBy] = managerId
                row[AttendanceRecordsTable.setAt] = now
            }
        }

        val events = client.get("/users/me/events") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
        }.body<List<EventWithTeams>>()

        val target = events.first { it.event.id == event.id }
        assertEquals(1, target.event.presentCount)
    }
}
