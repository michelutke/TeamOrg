package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.Team
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Focused IDOR-hardening tests: an authenticated caller without a role on the specific
 * resource must be rejected (403), even though the JWT itself is valid.
 */
class IdorHardeningTest : IntegrationTestBase() {

    @Serializable
    private data class CreateEventPayload(
        val title: String,
        val type: String,
        val startAt: String,
        val endAt: String,
        val teamIds: List<String> = emptyList()
    )

    @Serializable
    private data class EditEventPayload(val scope: String? = "this_only", val title: String? = null)

    @Serializable
    private data class CheckInPayload(val status: String, val note: String? = null)

    @Serializable
    private data class CreateAbwesenheitPayload(
        val id: String,
        val userId: String
    )

    @Serializable
    private data class CreateAbwesenheitRequestPayload(
        val presetType: String,
        val label: String,
        val ruleType: String,
        val startDate: String? = null,
        val endDate: String? = null
    )

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val client = createJsonClient()
        return client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    /** Creates a club + team owned by [token] (club_manager) and returns (clubId, teamId). */
    private suspend fun ApplicationTestBuilder.createClubAndTeam(token: String, name: String): Pair<String, String> {
        val client = createJsonClient()
        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("$name Club"))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("$name Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    @Test
    fun `PATCH event by non-member returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coach = register("idor_evt_coach@example.com")
        promoteToSuperAdmin(coach.userId)
        val (_, teamId) = createClubAndTeam(coach.token, "EvtMut")

        // Coach creates an event targeting their team
        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload("Team Event", "training", "2026-05-01T10:00:00Z", "2026-05-01T12:00:00Z", listOf(teamId)))
        }.body<Event>()

        // An unrelated authenticated user tries to edit it
        val outsider = register("idor_evt_outsider@example.com")
        val response = client.patch("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
            contentType(ContentType.Application.Json)
            setBody(EditEventPayload(scope = "this_only", title = "Hijacked"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET club by non-member returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val owner = register("idor_club_owner@example.com")
        promoteToSuperAdmin(owner.userId)
        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${owner.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Private Club"))
        }.body<Club>()

        // A user with no role in this club (member of nothing) is rejected
        val outsider = register("idor_club_outsider@example.com")
        val response = client.get("/clubs/${club.id}") {
            header(HttpHeaders.Authorization, "Bearer ${outsider.token}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `DELETE abwesenheit rule by non-owner returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val owner = register("idor_abw_owner@example.com")

        val rule = client.post("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${owner.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateAbwesenheitRequestPayload(
                presetType = "other",
                label = "Holiday",
                ruleType = "period",
                startDate = "2026-07-01",
                endDate = "2026-07-31"
            ))
        }.body<CreateAbwesenheitPayload>()

        // A different user attempts to delete the owner's rule
        val attacker = register("idor_abw_attacker@example.com")
        val response = client.delete("/users/me/abwesenheit/${rule.id}") {
            header(HttpHeaders.Authorization, "Bearer ${attacker.token}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        // The rule still exists for the owner
        val ownerRules = client.get("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${owner.token}")
        }.body<List<CreateAbwesenheitPayload>>()
        assertEquals(1, ownerRules.size)
    }

    @Test
    fun `check-in by coach of a different team returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        // Team A with its own coach and an event
        val coachA = register("idor_ci_coachA@example.com")
        promoteToSuperAdmin(coachA.userId)
        val (_, teamA) = createClubAndTeam(coachA.token, "TeamA")
        val event = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${coachA.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload("A Training", "training", "2026-05-02T10:00:00Z", "2026-05-02T12:00:00Z", listOf(teamA)))
        }.body<Event>()

        // Team B with a different coach (club_manager of an unrelated club)
        val coachB = register("idor_ci_coachB@example.com")
        promoteToSuperAdmin(coachB.userId)
        createClubAndTeam(coachB.token, "TeamB")

        // Coach B is a coach/club_manager — but NOT of team A — must be rejected on team A's event
        val target = register("idor_ci_target@example.com")
        val response = client.put("/events/${event.id}/check-in/${target.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
            contentType(ContentType.Application.Json)
            setBody(CheckInPayload(status = "present"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
