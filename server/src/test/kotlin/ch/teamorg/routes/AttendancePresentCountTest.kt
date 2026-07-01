package ch.teamorg.routes

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.repositories.AttendanceRepository
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class CreateEventPayloadPresentCount(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val teamIds: List<String> = emptyList()
)

class AttendancePresentCountTest : IntegrationTestBase() {

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
            setBody(CreateEventPayloadPresentCount(
                title = title,
                type = "training",
                startAt = "2026-09-01T10:00:00Z",
                endAt = "2026-09-01T12:00:00Z"
            ))
        }.body<Event>()
    }

    /**
     * Seeds two events and inserts attendance responses directly:
     * - eventE: 2 confirmed + 1 declined
     * - eventF: no responses
     * Presence now derives from attendance_responses where status = 'confirmed'.
     */
    private fun seedTwoEventsWithResponses(
        eventEId: UUID,
        userAId: UUID,
        userBId: UUID,
        coachId: UUID
    ) {
        transaction {
            val now = Instant.now()

            // userA: confirmed at eventE
            AttendanceResponsesTable.insert { row ->
                row[AttendanceResponsesTable.eventId] = eventEId
                row[AttendanceResponsesTable.userId] = userAId
                row[AttendanceResponsesTable.status] = "confirmed"
                row[AttendanceResponsesTable.respondedAt] = now
                row[AttendanceResponsesTable.updatedAt] = now
            }

            // userB: confirmed at eventE
            AttendanceResponsesTable.insert { row ->
                row[AttendanceResponsesTable.eventId] = eventEId
                row[AttendanceResponsesTable.userId] = userBId
                row[AttendanceResponsesTable.status] = "confirmed"
                row[AttendanceResponsesTable.respondedAt] = now
                row[AttendanceResponsesTable.updatedAt] = now
            }

            // coach: declined at eventE (should not be counted)
            AttendanceResponsesTable.insert { row ->
                row[AttendanceResponsesTable.eventId] = eventEId
                row[AttendanceResponsesTable.userId] = coachId
                row[AttendanceResponsesTable.status] = "declined"
                row[AttendanceResponsesTable.respondedAt] = now
                row[AttendanceResponsesTable.updatedAt] = now
            }

            // eventF: no responses inserted
        }
    }

    @Test
    fun `confirmedCounts groups confirmed responses by event`() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("pc_coach@example.com", displayName = "Coach PC")
        val userAAuth = registerAndLogin("pc_usera@example.com", displayName = "User A PC")
        val userBAuth = registerAndLogin("pc_userb@example.com", displayName = "User B PC")

        // Koin context is live after the app starts (first request triggers initialization)
        val repo = KoinJavaComponent.getKoin().get<AttendanceRepository>()

        val eventE = createEvent(coachAuth.token, "Event E PC")
        val eventF = createEvent(coachAuth.token, "Event F PC")

        val eventEId = UUID.fromString(eventE.id.toString())
        val eventFId = UUID.fromString(eventF.id.toString())
        val userAId = UUID.fromString(userAAuth.userId)
        val userBId = UUID.fromString(userBAuth.userId)
        val coachId = UUID.fromString(coachAuth.userId)

        seedTwoEventsWithResponses(eventEId, userAId, userBId, coachId)

        val counts = repo.confirmedCounts(listOf(eventEId, eventFId))

        assertEquals(2, counts[eventEId])
        assertEquals(null, counts[eventFId]) // no confirmed rows → not in map
    }
}
