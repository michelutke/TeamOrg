package ch.teamorg.routes

import ch.teamorg.db.tables.EventsTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Event
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class CreateEventPayloadFin(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val responseDeadline: String? = null,
    val teamIds: List<String> = emptyList(),
    val defaultResponse: String? = null
)

@Serializable
private data class FinalizeBlockedResponse(
    val reason: String,
    val userIds: List<String>
)

@Serializable
private data class AttendanceResponsePayloadFin(
    val eventId: String,
    val userId: String,
    val status: String,
    val unexcused: Boolean = false
)

@Serializable
private data class CoachResponsePayloadFin(val status: String, val unexcused: Boolean = false)

class FinalizeAttendanceTest : IntegrationTestBase() {

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

    private suspend fun ApplicationTestBuilder.setupClubAndTeam(token: String): Pair<String, String> {
        val client = createJsonClient()
        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Finalize Club"))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Finalize Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    private suspend fun ApplicationTestBuilder.createPastEvent(
        token: String,
        teamIds: List<String>,
        title: String = "Past Event",
        defaultResponse: String? = null
    ): Event {
        val client = createJsonClient()
        return client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadFin(
                title = title,
                type = "training",
                startAt = "2020-01-01T10:00:00Z",
                endAt = "2020-01-01T12:00:00Z",
                responseDeadline = "2020-01-01T09:00:00Z",
                teamIds = teamIds,
                defaultResponse = defaultResponse
            ))
        }.body<Event>()
    }

    private suspend fun ApplicationTestBuilder.invitePlayerToTeam(
        coachToken: String,
        teamId: String,
        playerToken: String
    ) {
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

    /** Sets the event's default_response and marks it as ended in the past. */
    private fun setDefaultResponse(eventId: String, defaultResponse: String) {
        transaction {
            EventsTable.update({ EventsTable.id eq UUID.fromString(eventId) }) {
                it[EventsTable.defaultResponse] = defaultResponse
            }
        }
    }

    /** Directly sets check_in_completed_at to simulate a finalized event. */
    private fun markEventDone(eventId: String) {
        transaction {
            EventsTable.update({ EventsTable.id eq UUID.fromString(eventId) }) {
                it[EventsTable.checkInCompletedAt] = Instant.now().minusSeconds(60)
            }
        }
    }

    // ---- (a) finalize with an unsure member → 409 + that userId, check_in_completed_at still null ---

    @Test
    fun `finalize with unsure member returns 409 and blocking userId`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_unsure_coach@example.com", displayName = "Coach Unsure")
        val playerAuth = registerAndLogin("fin_unsure_player@example.com", displayName = "Player Unsure")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))

        // Player submits unsure (deadline has passed so the player PUT will be blocked by
        // the cutoff guard — coach must override to get an unsure row)
        client.put("/events/${event.id}/attendance/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayloadFin(status = "unsure"))
        }

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<FinalizeBlockedResponse>()
        assertEquals("unsure", body.reason)
        assertTrue(body.userIds.contains(playerAuth.userId))

        // check_in_completed_at must remain null
        val fetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("awaiting_checkin", fetched.event.checkInStatus)
    }

    // ---- (b) default_response=accepted + no-response member → finalize 200, member confirmed ---

    @Test
    fun `finalize with default_response=accepted resolves no-response to confirmed`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_accepted_coach@example.com", displayName = "Coach Accepted")
        val playerAuth = registerAndLogin("fin_accepted_player@example.com", displayName = "Player Accepted")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))
        setDefaultResponse(event.id.toString(), "accepted")

        // Player has not responded

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Member's response should now be confirmed
        val entries = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<AttendanceResponsePayloadFin>>()
        val playerEntry = entries.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry)
        assertEquals("confirmed", playerEntry.status)

        // Event should be done
        val fetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("done", fetched.event.checkInStatus)
    }

    // ---- (c) default_response=none + no-response member → 409 BlockedNoResponse ---

    @Test
    fun `finalize with default_response=none and no-response member returns 409 BlockedNoResponse`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_none_coach@example.com", displayName = "Coach None")
        val playerAuth = registerAndLogin("fin_none_player@example.com", displayName = "Player None")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))
        setDefaultResponse(event.id.toString(), "none")

        // Player has not responded

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<FinalizeBlockedResponse>()
        assertEquals("no-response", body.reason)
        assertTrue(body.userIds.contains(playerAuth.userId))
    }

    // ---- (d) finalize before end_at → 409 ---

    @Test
    fun `finalize before event end_at returns 409`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_early_coach@example.com", displayName = "Coach Early")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)

        // Future event
        val futureEvent = client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadFin(
                title = "Future Event",
                type = "training",
                startAt = "2099-01-01T10:00:00Z",
                endAt = "2099-01-01T12:00:00Z",
                teamIds = listOf(teamId)
            ))
        }.body<Event>()

        val response = client.post("/events/${futureEvent.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ---- (e) reopen a done event → checkInStatus back to awaiting_checkin ---

    @Test
    fun `reopen a done event sets checkInStatus back to awaiting_checkin`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_reopen_coach@example.com", displayName = "Coach Reopen")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))
        markEventDone(event.id.toString())

        // Verify it is done first
        val doneFetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("done", doneFetched.event.checkInStatus)

        val response = client.post("/events/${event.id}/attendance/reopen") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val reopenedFetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("awaiting_checkin", reopenedFetched.event.checkInStatus)
    }

    // ---- coach edit on a done event → 409 (real finalize, not placeholder) ---

    @Test
    fun `coach edit on a finalized event returns 409`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_coedit_coach@example.com", displayName = "Coach CoEdit")
        val playerAuth = registerAndLogin("fin_coedit_player@example.com", displayName = "Player CoEdit")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))
        setDefaultResponse(event.id.toString(), "accepted")

        // Finalize the event (should succeed with default_response=accepted)
        val finalizeResp = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, finalizeResp.status)

        // Now attempt coach edit → must be rejected
        val editResp = client.put("/events/${event.id}/attendance/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayloadFin(status = "declined"))
        }

        assertEquals(HttpStatusCode.Conflict, editResp.status)
    }

    // ---- player cannot finalize (IDOR/auth guard) ---

    @Test
    fun `player cannot call finalize route`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_guard_coach@example.com", displayName = "Coach Guard Fin")
        val playerAuth = registerAndLogin("fin_guard_player@example.com", displayName = "Player Guard Fin")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId))

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- Rec#4: defaultResponse persisted via the CREATE API (no raw SQL) drives finalize ----

    @Test
    fun `defaultResponse=accepted set via create API resolves no-response to confirmed on finalize`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_api_acc_coach@example.com", displayName = "Coach ApiAcc")
        val playerAuth = registerAndLogin("fin_api_acc_player@example.com", displayName = "Player ApiAcc")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        // default_response is set through the create route only — the column must be wired end-to-end.
        val event = createPastEvent(coachAuth.token, listOf(teamId), defaultResponse = "accepted")
        assertEquals("accepted", event.defaultResponse)

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val entries = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<AttendanceResponsePayloadFin>>()
        val playerEntry = entries.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry)
        assertEquals("confirmed", playerEntry.status)

        val fetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("done", fetched.event.checkInStatus)
    }

    @Test
    fun `defaultResponse=none set via create API blocks finalize on a no-response member`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("fin_api_none_coach@example.com", displayName = "Coach ApiNone")
        val playerAuth = registerAndLogin("fin_api_none_player@example.com", displayName = "Player ApiNone")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createPastEvent(coachAuth.token, listOf(teamId), defaultResponse = "none")
        assertEquals("none", event.defaultResponse)

        val response = client.post("/events/${event.id}/attendance/finalize") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<FinalizeBlockedResponse>()
        assertEquals("no-response", body.reason)
        assertTrue(body.userIds.contains(playerAuth.userId))

        val fetched = client.get("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<EventWithTeams>()
        assertEquals("awaiting_checkin", fetched.event.checkInStatus)
    }
}
