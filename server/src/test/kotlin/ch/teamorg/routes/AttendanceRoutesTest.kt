package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.Team
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private data class AttendanceSubmitPayload(val status: String, val reason: String? = null)

@Serializable
private data class AttendanceResponsePayload(
    val eventId: String,
    val userId: String,
    val status: String,
    val reason: String? = null
)

@Serializable
private data class CreateEventPayloadAttn(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val responseDeadline: String? = null,
    val teamIds: List<String> = emptyList()
)

class AttendanceRoutesTest : IntegrationTestBase() {

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
            setBody(CreateClubRequest("Attendance Club"))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Attendance Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    private suspend fun ApplicationTestBuilder.createEvent(
        token: String,
        title: String,
        responseDeadline: String? = null,
        teamIds: List<String> = emptyList(),
        startAt: String = "2026-09-01T10:00:00Z",
        endAt: String = "2026-09-01T12:00:00Z"
    ): Event {
        val client = createJsonClient()
        return client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadAttn(
                title = title,
                type = "training",
                startAt = startAt,
                endAt = endAt,
                responseDeadline = responseDeadline,
                teamIds = teamIds
            ))
        }.body<Event>()
    }

    @Serializable
    private data class CreateAbwesenheitPayloadAttn(
        val presetType: String,
        val label: String,
        val ruleType: String,
        val weekdays: List<Int>? = null,
        val startDate: String? = null,
        val endDate: String? = null
    )

    @Test
    fun `submit response returns 200 with valid status`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("att_ok@example.com")
        val event = createEvent(auth.token, "Training Ok")

        val response = client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AttendanceResponsePayload>()
        assertEquals("confirmed", body.status)
        assertEquals(event.id.toString(), body.eventId)
    }

    @Test
    fun `submit unsure without reason returns 400`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("att_unsure_nok@example.com")
        val event = createEvent(auth.token, "Training Unsure Bad")

        val response = client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "unsure"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `submit unsure with reason returns 200`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("att_unsure_ok@example.com")
        val event = createEvent(auth.token, "Training Unsure Good")

        val response = client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "unsure", reason = "might be late"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AttendanceResponsePayload>()
        assertEquals("unsure", body.status)
    }

    @Test
    fun `player edit after cutoff returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("att_deadline@example.com")
        // Past deadline — cutoff = response_deadline (in the past)
        val event = createEvent(auth.token, "Training Past Deadline", responseDeadline = "2020-01-01T00:00:00Z")

        val response = client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `get event attendance returns all responses`() = withTeamorgTestApplication {
        val client = createJsonClient()
        // Attendance is now membership-scoped: set up a real team and add two players,
        // both members of the event's team.
        val coachAuth = registerAndLogin("att_list_coach@example.com", displayName = "Coach")
        val player1 = registerAndLogin("att_list1@example.com", displayName = "Player 1")
        val player2 = registerAndLogin("att_list2@example.com", displayName = "Player 2")
        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, player1.token)
        invitePlayerToTeam(coachAuth.token, teamId, player2.token)
        val event = createEvent(coachAuth.token, "Training List", teamIds = listOf(teamId))

        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${player1.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }
        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${player2.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "declined"))
        }

        // A team member may read the event's attendance.
        val response = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${player1.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entries = response.body<List<AttendanceResponsePayload>>()
        assertEquals(2, entries.size)
    }

    @Serializable
    private data class AttendanceResponseDtoPayload(
        val eventId: String,
        val userId: String,
        val status: String,
        val reason: String? = null,
        val abwesenheitRuleId: String? = null,
        val manualOverride: Boolean = false,
        val unexcused: Boolean = false,
        val respondedAt: String? = null,
        val updatedAt: String
    )

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

    @Test
    fun `get event attendance returns correct DTO fields`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("dto_check@example.com", displayName = "DTO User")
        val event = createEvent(auth.token, "Training DTO Check")

        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }

        val response = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entries = response.body<List<AttendanceResponseDtoPayload>>()
        assertEquals(1, entries.size)

        val entry = entries[0]
        assertEquals(event.id.toString(), entry.eventId)
        assertEquals(auth.userId, entry.userId)
        assertEquals("confirmed", entry.status)
        // updatedAt must be a non-blank ISO string, not a UUID or raw java.time value
        assertTrue(entry.updatedAt.isNotBlank())
        assertTrue(entry.updatedAt.contains("T"), "updatedAt should be ISO-8601: ${entry.updatedAt}")
    }

    @Test
    fun `submit response updates existing response`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("update_response@example.com", displayName = "Updater")
        val event = createEvent(auth.token, "Training Update Response")

        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }

        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "declined"))
        }

        val meResponse = client.get("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, meResponse.status)
        val body = meResponse.body<AttendanceResponsePayload>()
        assertEquals("declined", body.status)
    }

    @Test
    fun `get my response returns NoContent when no response exists`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = registerAndLogin("no_response_yet@example.com", displayName = "No Response")
        val event = createEvent(auth.token, "Training No Response")

        val response = client.get("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    // --- absence auto-decline on event creation ---

    @Test
    fun `event created during absence period gets auto-declined`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("abw_period_coach@example.com", displayName = "Coach Period")
        val playerAuth = registerAndLogin("abw_period_player@example.com", displayName = "Player Period")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        // Player creates a period absence rule covering October 2026
        client.post("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateAbwesenheitPayloadAttn(
                presetType = "other",
                label = "October sick",
                ruleType = "period",
                startDate = "2026-10-01",
                endDate = "2026-10-31"
            ))
        }

        // Coach creates event in October linked to the team
        val event = createEvent(
            coachAuth.token,
            "October Training",
            teamIds = listOf(teamId),
            startAt = "2026-10-15T10:00:00Z",
            endAt = "2026-10-15T12:00:00Z"
        )

        val response = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entries = response.body<List<AttendanceResponseDtoPayload>>()
        val playerEntry = entries.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry)
        assertEquals("declined-auto", playerEntry.status)
        assertNotNull(playerEntry.abwesenheitRuleId)
    }

    @Test
    fun `event created on recurring absence day gets auto-declined`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("abw_recur_coach@example.com", displayName = "Coach Recur")
        val playerAuth = registerAndLogin("abw_recur_player@example.com", displayName = "Player Recur")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        // Player creates recurring rule for Wednesdays (dayOfWeek.value % 7 = 3)
        client.post("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateAbwesenheitPayloadAttn(
                presetType = "work",
                label = "Wednesday work",
                ruleType = "recurring",
                weekdays = listOf(3)
            ))
        }

        // 2026-09-02 is a Wednesday
        val event = createEvent(
            coachAuth.token,
            "Wednesday Training",
            teamIds = listOf(teamId),
            startAt = "2026-09-02T10:00:00Z",
            endAt = "2026-09-02T12:00:00Z"
        )

        val response = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entries = response.body<List<AttendanceResponseDtoPayload>>()
        val playerEntry = entries.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry)
        assertEquals("declined-auto", playerEntry.status)
    }

    @Test
    fun `manual response overrides auto-decline`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("abw_override_coach@example.com", displayName = "Coach Override")
        val playerAuth = registerAndLogin("abw_override_player@example.com", displayName = "Player Override")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        // Player has a period absence rule
        client.post("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateAbwesenheitPayloadAttn(
                presetType = "other",
                label = "Holiday",
                ruleType = "period",
                startDate = "2026-11-01",
                endDate = "2026-11-30"
            ))
        }

        val event = createEvent(
            coachAuth.token,
            "November Training",
            teamIds = listOf(teamId),
            startAt = "2026-11-10T10:00:00Z",
            endAt = "2026-11-10T12:00:00Z"
        )

        // Verify auto-declined first
        val beforeEntries = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<AttendanceResponseDtoPayload>>()
        assertEquals("declined-auto", beforeEntries.find { it.userId == playerAuth.userId }?.status)

        // Player manually submits confirmed
        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayload(status = "confirmed"))
        }

        val afterEntries = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<AttendanceResponseDtoPayload>>()
        val playerEntry = afterEntries.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry)
        assertEquals("confirmed", playerEntry.status)
    }

    // --- Task 3: coach edit, cutoff lock, unexcused ---

    @Serializable
    private data class CoachResponsePayload(val status: String, val unexcused: Boolean = false)

    @Test
    fun `coach edit after cutoff succeeds and persists`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("coach_cutoff@example.com", displayName = "Coach Cutoff")
        val playerAuth = registerAndLogin("player_cutoff@example.com", displayName = "Player Cutoff")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        // Event with a past response deadline — player can't edit, but coach can
        val event = createEvent(
            coachAuth.token,
            "Training Cutoff Coach",
            teamIds = listOf(teamId),
            responseDeadline = "2020-01-01T00:00:00Z"
        )

        val response = client.put("/events/${event.id}/attendance/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayload(status = "confirmed"))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Verify persisted
        val entries = client.get("/events/${event.id}/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<AttendanceResponseDtoPayload>>()
        assertEquals("confirmed", entries.find { it.userId == playerAuth.userId }?.status)
    }

    @Test
    fun `coach sets declined with unexcused flag`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("coach_unexcused@example.com", displayName = "Coach Unexcused")
        val playerAuth = registerAndLogin("player_unexcused@example.com", displayName = "Player Unexcused")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(coachAuth.token, "Training Unexcused", teamIds = listOf(teamId))

        val response = client.put("/events/${event.id}/attendance/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayload(status = "declined", unexcused = true))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AttendanceResponseDtoPayload>()
        assertEquals("declined", body.status)
        assertEquals(true, body.unexcused)
    }

    @Test
    fun `player rejected from coach attendance edit route (IDOR guard)`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("coach_done@example.com", displayName = "Coach Done")
        val playerAuth = registerAndLogin("player_done@example.com", displayName = "Player Done")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(
            coachAuth.token,
            "Training IDOR Guard",
            teamIds = listOf(teamId),
            startAt = "2020-01-01T10:00:00Z",
            endAt = "2020-01-01T12:00:00Z"
        )

        // Player (not a coach) trying to use the coach route must be rejected
        val response = client.put("/events/${event.id}/attendance/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayload(status = "confirmed"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `player cannot use coach edit route`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = registerAndLogin("coach_idor@example.com", displayName = "Coach IDOR")
        val player1Auth = registerAndLogin("player_idor1@example.com", displayName = "Player IDOR 1")
        val player2Auth = registerAndLogin("player_idor2@example.com", displayName = "Player IDOR 2")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayerToTeam(coachAuth.token, teamId, player1Auth.token)
        invitePlayerToTeam(coachAuth.token, teamId, player2Auth.token)

        val event = createEvent(coachAuth.token, "Training IDOR Guard", teamIds = listOf(teamId))

        // player1 tries to overwrite player2's attendance — must be forbidden
        val response = client.put("/events/${event.id}/attendance/${player2Auth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${player1Auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CoachResponsePayload(status = "declined"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
