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
import kotlin.test.assertTrue

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
    private data class CoachResponsePayload(val status: String, val unexcused: Boolean = false)

    @Serializable
    private data class SubGroupPayload(val id: String, val teamId: String, val name: String, val memberCount: Long = 0)

    @Serializable
    private data class SubmitResponsePayload(val status: String, val reason: String? = null)

    @Serializable
    private data class RawAttendancePayload(
        val eventId: String,
        val userId: String,
        val responseStatus: String? = null,
        val recordStatus: String? = null,
        val eventStartAt: String
    )

    /** Invites a player to [teamId] and redeems with [playerToken]. */
    private suspend fun ApplicationTestBuilder.addPlayer(coachToken: String, teamId: String, playerToken: String) {
        val client = createJsonClient()
        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer $coachToken")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
        }
    }

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
    fun `coach attendance edit by coach of a different team returns 403`() = withTeamorgTestApplication {
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
        val response = client.put("/events/${event.id}/attendance/${target.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayload(status = "confirmed"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `subgroup mutation by coach of a different team returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        // Team A coach
        val coachA = register("idor_sg_coachA@example.com")
        promoteToSuperAdmin(coachA.userId)
        val (_, teamA) = createClubAndTeam(coachA.token, "SgTeamA")

        // Team B coach with a subgroup owned by team B
        val coachB = register("idor_sg_coachB@example.com")
        promoteToSuperAdmin(coachB.userId)
        val (_, teamB) = createClubAndTeam(coachB.token, "SgTeamB")
        val subGroupB = client.post("/teams/$teamB/subgroups") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateSubGroupRequest("Team B Group"))
        }.body<SubGroupPayload>()

        // Coach A is a legitimate coach of team A, but forges team B's subgroup id into team A's path.
        val putResponse = client.put("/teams/$teamA/subgroups/${subGroupB.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachA.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateSubGroupRequest("Hijacked"))
        }
        assertEquals(HttpStatusCode.Forbidden, putResponse.status)

        val deleteResponse = client.delete("/teams/$teamA/subgroups/${subGroupB.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachA.token}")
        }
        assertEquals(HttpStatusCode.Forbidden, deleteResponse.status)

        // The subgroup still exists for team B
        val listB = client.get("/teams/$teamB/subgroups") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
        }.body<List<SubGroupPayload>>()
        assertEquals(1, listB.size)
        assertEquals("Team B Group", listB[0].name)
    }

    @Test
    fun `user attendance is scoped to teams shared with the caller`() = withTeamorgTestApplication {
        val client = createJsonClient()

        // Coach A manages team A only
        val coachA = register("idor_ua_coachA@example.com")
        promoteToSuperAdmin(coachA.userId)
        val (_, teamA) = createClubAndTeam(coachA.token, "UaTeamA")

        // Coach B manages team B only
        val coachB = register("idor_ua_coachB@example.com")
        promoteToSuperAdmin(coachB.userId)
        val (_, teamB) = createClubAndTeam(coachB.token, "UaTeamB")

        // Target player is a member of BOTH teams
        val target = register("idor_ua_target@example.com")
        addPlayer(coachA.token, teamA, target.token)
        addPlayer(coachB.token, teamB, target.token)

        // Event in team A, target RSVPs
        val eventA = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${coachA.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload("A Event", "training", "2026-08-01T10:00:00Z", "2026-08-01T12:00:00Z", listOf(teamA)))
        }.body<Event>()
        client.put("/events/${eventA.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${target.token}")
            contentType(ContentType.Application.Json)
            setBody(SubmitResponsePayload("confirmed"))
        }

        // Event in team B, target RSVPs
        val eventB = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${coachB.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayload("B Event", "training", "2026-08-02T10:00:00Z", "2026-08-02T12:00:00Z", listOf(teamB)))
        }.body<Event>()
        client.put("/events/${eventB.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${target.token}")
            contentType(ContentType.Application.Json)
            setBody(SubmitResponsePayload("confirmed"))
        }

        // Coach A (shares only team A) reads the target's attendance — must NOT see team B's event
        val response = client.get("/users/${target.userId}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachA.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val rows = response.body<List<RawAttendancePayload>>()
        assertTrue(rows.any { it.eventId == eventA.id.toString() }, "Coach A should see the shared team A event")
        assertTrue(rows.none { it.eventId == eventB.id.toString() }, "Coach A must NOT see the unshared team B event")
    }
}
