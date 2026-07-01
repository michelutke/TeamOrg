package ch.teamorg.routes

import ch.teamorg.db.tables.EventsTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.EventWithTeams
import ch.teamorg.domain.models.Team
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
import kotlin.test.assertTrue

@Serializable
private data class CreateEventPayloadACI(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val teamIds: List<String> = emptyList()
)

class AwaitingCheckInTest : IntegrationTestBase() {

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

    private suspend fun ApplicationTestBuilder.setupClubAndTeam(token: String, clubName: String = "ACI Club"): Pair<String, String> {
        val client = createJsonClient()
        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(clubName))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("ACI Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    private suspend fun ApplicationTestBuilder.createEvent(
        token: String,
        title: String,
        startAt: String,
        endAt: String,
        teamIds: List<String> = emptyList()
    ): String {
        val client = createJsonClient()
        val resp = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadACI(title = title, type = "training", startAt = startAt, endAt = endAt, teamIds = teamIds))
        }
        return resp.body<kotlinx.serialization.json.JsonObject>()["id"]!!.toString().trim('"')
    }

    /** Force check_in_completed_at to a non-null value to simulate a finalized event. */
    private fun finalizeEvent(eventId: String) {
        transaction {
            EventsTable.update({ EventsTable.id eq UUID.fromString(eventId) }) {
                it[EventsTable.checkInCompletedAt] = Instant.now().minusSeconds(60)
            }
        }
    }

    @Test
    fun `past unfinalized event on coached team appears in awaiting-checkin`() = withTeamorgTestApplication {
        val coach = registerAndLogin("aci_coach1@example.com", displayName = "Coach1")
        promoteToSuperAdmin(coach.userId)
        val (_, teamId) = setupClubAndTeam(coach.token, "ACI Club 1")
        val eventId = createEvent(
            token = coach.token,
            title = "Past Unfinalized",
            startAt = "2020-01-01T10:00:00Z",
            endAt = "2020-01-01T12:00:00Z",
            teamIds = listOf(teamId)
        )

        val client = createJsonClient()
        val resp = client.get("/users/me/events/awaiting-checkin") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val events = resp.body<List<EventWithTeams>>()
        assertTrue(events.any { it.event.id.toString() == eventId }, "Past unfinalized event should appear")
    }

    @Test
    fun `finalized past event is absent from awaiting-checkin`() = withTeamorgTestApplication {
        val coach = registerAndLogin("aci_coach2@example.com", displayName = "Coach2")
        promoteToSuperAdmin(coach.userId)
        val (_, teamId) = setupClubAndTeam(coach.token, "ACI Club 2")
        val eventId = createEvent(
            token = coach.token,
            title = "Finalized Past",
            startAt = "2020-02-01T10:00:00Z",
            endAt = "2020-02-01T12:00:00Z",
            teamIds = listOf(teamId)
        )
        finalizeEvent(eventId)

        val client = createJsonClient()
        val events = client.get("/users/me/events/awaiting-checkin") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }.body<List<EventWithTeams>>()
        assertTrue(events.none { it.event.id.toString() == eventId }, "Finalized event must not appear")
    }

    @Test
    fun `future event is absent from awaiting-checkin`() = withTeamorgTestApplication {
        val coach = registerAndLogin("aci_coach3@example.com", displayName = "Coach3")
        promoteToSuperAdmin(coach.userId)
        val (_, teamId) = setupClubAndTeam(coach.token, "ACI Club 3")
        val eventId = createEvent(
            token = coach.token,
            title = "Future Event",
            startAt = "2099-01-01T10:00:00Z",
            endAt = "2099-01-01T12:00:00Z",
            teamIds = listOf(teamId)
        )

        val client = createJsonClient()
        val events = client.get("/users/me/events/awaiting-checkin") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }.body<List<EventWithTeams>>()
        assertTrue(events.none { it.event.id.toString() == eventId }, "Future event must not appear")
    }

    @Test
    fun `event on another club's team the user does not coach is absent`() = withTeamorgTestApplication {
        // coachA creates their own club/team and a past event
        val coachA = registerAndLogin("aci_coacha@example.com", displayName = "CoachA")
        promoteToSuperAdmin(coachA.userId)
        val (_, teamA) = setupClubAndTeam(coachA.token, "ACI Club A")
        createEvent(
            token = coachA.token,
            title = "CoachA Event",
            startAt = "2020-03-01T10:00:00Z",
            endAt = "2020-03-01T12:00:00Z",
            teamIds = listOf(teamA)
        )

        // coachB registers but has no relation to coachA's team
        val coachB = registerAndLogin("aci_coachb@example.com", displayName = "CoachB")
        promoteToSuperAdmin(coachB.userId)
        setupClubAndTeam(coachB.token, "ACI Club B")

        val client = createJsonClient()
        val events = client.get("/users/me/events/awaiting-checkin") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
        }.body<List<EventWithTeams>>()

        // coachB's list must not contain coachA's event
        assertTrue(events.none { it.event.title == "CoachA Event" }, "Cross-club event must not appear")
    }
}
