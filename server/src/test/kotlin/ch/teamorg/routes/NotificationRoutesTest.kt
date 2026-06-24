package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Event
import ch.teamorg.domain.models.NotificationResponse
import ch.teamorg.domain.models.NotificationSettingsResponse
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.NotificationRepository
import ch.teamorg.infra.PushService
import ch.teamorg.infra.fireCoachSummaries
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Local DTOs used only in tests
// ---------------------------------------------------------------------------

@Serializable
private data class CreateEventPayloadNotif(
    val title: String,
    val type: String,
    val startAt: String,
    val endAt: String,
    val teamIds: List<String> = emptyList()
)

@Serializable
private data class EditEventPayloadNotif(
    val scope: String? = "this_only",
    val title: String? = null
)

@Serializable
private data class CancelPayloadNotif(val scope: String? = "this_only")

@Serializable
private data class AttendanceSubmitPayloadNotif(val status: String, val reason: String? = null)

@Serializable
private data class UpdateSettingsPayloadNotif(
    val eventsNew: Boolean? = null,
    val eventsEdit: Boolean? = null,
    val eventsCancel: Boolean? = null,
    val remindersEnabled: Boolean? = null,
    val reminderLeadMinutes: Int? = null,
    val coachResponseMode: String? = null,
    val absencesEnabled: Boolean? = null
)

@Serializable
private data class ReminderOverridePayloadNotif(val reminderLeadMinutes: Int?)

@Serializable
private data class UnreadCountResponseNotif(val count: Long)

@Serializable
private data class MarkAllReadResponseNotif(val marked: Int)

@Serializable
private data class ReminderOverrideResponseNotif(val reminderLeadMinutes: Int?)

@Serializable
private data class CreateAbwesenheitPayloadNotif(
    val presetType: String,
    val label: String,
    val ruleType: String,
    val weekdays: List<Int>? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

// ---------------------------------------------------------------------------
// Test class
// ---------------------------------------------------------------------------

class NotificationRoutesTest : IntegrationTestBase() {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
            setBody(CreateClubRequest("Notif Club"))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Notif Team"))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    private suspend fun ApplicationTestBuilder.invitePlayer(
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

    /** Add the coach to team_roles as "coach" so getCoachIdsForTeam() finds them. */
    private suspend fun ApplicationTestBuilder.addCoachToTeam(coachToken: String, teamId: String) {
        val client = createJsonClient()
        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer $coachToken")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "coach"))
        }.body<InviteResponse>().token
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer $coachToken")
        }
    }

    private suspend fun ApplicationTestBuilder.createEvent(
        token: String,
        title: String,
        teamIds: List<String> = emptyList(),
        startAt: String = "2028-06-01T10:00:00Z",
        endAt: String = "2028-06-01T12:00:00Z"
    ): Event {
        val client = createJsonClient()
        return client.post("/events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateEventPayloadNotif(
                title = title,
                type = "training",
                startAt = startAt,
                endAt = endAt,
                teamIds = teamIds
            ))
        }.body<Event>()
    }

    private suspend fun ApplicationTestBuilder.getNotifications(token: String): List<NotificationResponse> {
        val client = createJsonClient()
        return client.get("/notifications") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<NotificationResponse>>()
    }

    // ------------------------------------------------------------------
    // Inbox tests (NO-11)
    // ------------------------------------------------------------------

    @Test
    fun inbox_emptyByDefault() = withTeamorgTestApplication {
        val auth = registerAndLogin("notif_empty@example.com")
        val notifications = getNotifications(auth.token)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun inbox_returnsNotifications() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("inbox_coach@example.com", displayName = "Coach")
        val playerAuth = registerAndLogin("inbox_player@example.com", displayName = "Player")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)
        createEvent(coachAuth.token, "Inbox Training", teamIds = listOf(teamId))
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        assertTrue(notifications.isNotEmpty(), "Player should have at least 1 notification")
        assertTrue(notifications.any { it.type == "event_new" }, "Should have event_new notification")
    }

    @Test
    fun inbox_markRead() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("mark_read_coach@example.com", displayName = "Coach MR")
        val playerAuth = registerAndLogin("mark_read_player@example.com", displayName = "Player MR")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)
        createEvent(coachAuth.token, "Mark Read Training", teamIds = listOf(teamId))
        delay(300)

        val client = createJsonClient()
        val notifications = getNotifications(playerAuth.token)
        assertTrue(notifications.isNotEmpty())

        val notifId = notifications.first().id
        val markResponse = client.post("/notifications/$notifId/read") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, markResponse.status)

        val after = getNotifications(playerAuth.token)
        val marked = after.find { it.id == notifId }
        assertNotNull(marked)
        assertTrue(marked.isRead)
    }

    @Test
    fun inbox_markAllRead() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("mark_all_coach@example.com", displayName = "Coach MA")
        val playerAuth = registerAndLogin("mark_all_player@example.com", displayName = "Player MA")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)
        createEvent(coachAuth.token, "Mark All Training", teamIds = listOf(teamId))
        delay(300)

        val client = createJsonClient()
        val markAllResponse = client.post("/notifications/read-all") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, markAllResponse.status)
        val body = markAllResponse.body<MarkAllReadResponseNotif>()
        assertTrue(body.marked >= 0)

        val after = getNotifications(playerAuth.token)
        assertTrue(after.all { it.isRead }, "All notifications should be read after mark-all")
    }

    @Test
    fun inbox_unreadCount() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("unread_coach@example.com", displayName = "Coach UC")
        val playerAuth = registerAndLogin("unread_player@example.com", displayName = "Player UC")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)
        createEvent(coachAuth.token, "Unread Count Training", teamIds = listOf(teamId))
        delay(300)

        val client = createJsonClient()
        val response = client.get("/notifications/unread-count") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val countBody = response.body<UnreadCountResponseNotif>()
        assertTrue(countBody.count >= 1, "Should have at least 1 unread notification")
    }

    // ------------------------------------------------------------------
    // Settings tests (NO-08)
    // ------------------------------------------------------------------

    @Test
    fun settings_defaultValues() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("settings_default@example.com")
        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)

        val client = createJsonClient()
        val response = client.get("/notifications/settings/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val settings = response.body<NotificationSettingsResponse>()
        assertTrue(settings.eventsNew)
        assertTrue(settings.eventsEdit)
        assertTrue(settings.eventsCancel)
        assertTrue(settings.remindersEnabled)
        assertEquals(120, settings.reminderLeadMinutes)
        assertEquals("per_response", settings.coachResponseMode)
        assertTrue(settings.absencesEnabled)
    }

    @Test
    fun settings_update() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("settings_update@example.com")
        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)

        val client = createJsonClient()
        val putResponse = client.put("/notifications/settings/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsPayloadNotif(eventsNew = false))
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)

        val updated = client.get("/notifications/settings/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<NotificationSettingsResponse>()
        assertEquals(false, updated.eventsNew)
        assertEquals(true, updated.eventsEdit, "Other settings should remain default")
    }

    @Test
    fun settings_perTeam() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("settings_perteam@example.com")
        promoteToSuperAdmin(coachAuth.userId)
        val client = createJsonClient()

        val clubA = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Club A PT"))
        }.body<Club>()
        val teamA = client.post("/clubs/${clubA.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Team A"))
        }.body<Team>()

        val clubB = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Club B PT"))
        }.body<Club>()
        val teamB = client.post("/clubs/${clubB.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Team B"))
        }.body<Team>()

        client.put("/notifications/settings/${teamA.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsPayloadNotif(eventsNew = false))
        }

        val teamASettings = client.get("/notifications/settings/${teamA.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<NotificationSettingsResponse>()
        assertEquals(false, teamASettings.eventsNew)

        val teamBSettings = client.get("/notifications/settings/${teamB.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<NotificationSettingsResponse>()
        assertEquals(true, teamBSettings.eventsNew, "Team B settings should remain default")
    }

    // ------------------------------------------------------------------
    // Reminder override tests (NO-09)
    // ------------------------------------------------------------------

    @Test
    fun reminderOverride_default() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("reminder_default@example.com")
        val event = createEvent(coachAuth.token, "Reminder Default Event")

        val client = createJsonClient()
        val response = client.get("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ReminderOverrideResponseNotif>()
        assertNull(body.reminderLeadMinutes)
    }

    @Test
    fun reminderOverride_set() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("reminder_set@example.com")
        val event = createEvent(coachAuth.token, "Reminder Set Event")

        val client = createJsonClient()
        client.put("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(ReminderOverridePayloadNotif(reminderLeadMinutes = 60))
        }

        val body = client.get("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<ReminderOverrideResponseNotif>()
        assertEquals(60, body.reminderLeadMinutes)
    }

    @Test
    fun reminderOverride_remove() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("reminder_remove@example.com")
        val event = createEvent(coachAuth.token, "Reminder Remove Event")

        val client = createJsonClient()
        client.put("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(ReminderOverridePayloadNotif(reminderLeadMinutes = 45))
        }
        client.put("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(ReminderOverridePayloadNotif(reminderLeadMinutes = null))
        }

        val body = client.get("/events/${event.id}/reminder") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<ReminderOverrideResponseNotif>()
        assertNull(body.reminderLeadMinutes)
    }

    // ------------------------------------------------------------------
    // Event trigger tests (NO-01, NO-03, NO-04)
    // ------------------------------------------------------------------

    @Test
    fun createEvent_triggersNotification() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("trig_create_coach@example.com", displayName = "Coach Create")
        val playerAuth = registerAndLogin("trig_create_player@example.com", displayName = "Player Create")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)
        createEvent(coachAuth.token, "Create Trigger", teamIds = listOf(teamId))
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        assertTrue(notifications.any { it.type == "event_new" }, "Player should have event_new notification")
    }

    @Test
    fun editEvent_triggersNotification() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("trig_edit_coach@example.com", displayName = "Coach Edit")
        val playerAuth = registerAndLogin("trig_edit_player@example.com", displayName = "Player Edit")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(coachAuth.token, "Edit Trigger", teamIds = listOf(teamId))
        delay(100)

        val client = createJsonClient()
        client.patch("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(EditEventPayloadNotif(scope = "this_only", title = "Edited Title"))
        }
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        assertTrue(notifications.any { it.type == "event_edit" }, "Player should have event_edit notification")
    }

    @Test
    fun cancelEvent_triggersNotification() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("trig_cancel_coach@example.com", displayName = "Coach Cancel")
        val playerAuth = registerAndLogin("trig_cancel_player@example.com", displayName = "Player Cancel")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(coachAuth.token, "Cancel Trigger", teamIds = listOf(teamId))
        delay(100)

        val client = createJsonClient()
        client.post("/events/${event.id}/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CancelPayloadNotif(scope = "this_only"))
        }
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        assertTrue(notifications.any { it.type == "event_cancel" }, "Player should have event_cancel notification")
    }

    // ------------------------------------------------------------------
    // Coach response mode tests (NO-05, NO-06)
    // ------------------------------------------------------------------

    @Test
    fun coachResponse_perResponse() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("coach_per_resp@example.com", displayName = "Coach PerResp")
        val playerAuth = registerAndLogin("player_per_resp@example.com", displayName = "Player PerResp")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        // Coach must be in team_roles with "coach" role for getCoachIdsForTeam() to find them
        addCoachToTeam(coachAuth.token, teamId)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(coachAuth.token, "PerResp Training", teamIds = listOf(teamId))
        delay(100)

        val client = createJsonClient()
        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayloadNotif(status = "confirmed"))
        }
        delay(300)

        val coachNotifications = getNotifications(coachAuth.token)
        assertTrue(
            coachNotifications.any { it.type == "response" },
            "Coach should have immediate response notification (per_response mode)"
        )
    }

    @Test
    fun coachResponse_summary() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("coach_summary_mode@example.com", displayName = "Coach Summary")
        val playerAuth = registerAndLogin("player_summary_mode@example.com", displayName = "Player Summary")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        // Coach must be in team_roles with "coach" role for getCoachIdsForTeam() to find them
        addCoachToTeam(coachAuth.token, teamId)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val client = createJsonClient()
        // Set coach notification mode to 'summary'
        client.put("/notifications/settings/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsPayloadNotif(coachResponseMode = "summary"))
        }

        // Create event starting within 120 min (coach summary window)
        val nowEpoch = java.time.Instant.now()
        val startAt = nowEpoch.plusSeconds(60 * 60).toString() // 1 hour from now
        val endAt = nowEpoch.plusSeconds(2 * 60 * 60).toString()
        val event = createEvent(
            coachAuth.token, "Summary Training",
            teamIds = listOf(teamId), startAt = startAt, endAt = endAt
        )
        delay(100)

        // Player RSVPs — coach should NOT get immediate response notification
        client.put("/events/${event.id}/attendance/me") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(AttendanceSubmitPayloadNotif(status = "confirmed"))
        }
        delay(300)

        val coachNotifsBefore = getNotifications(coachAuth.token)
        assertTrue(
            coachNotifsBefore.none { it.type == "response" },
            "Coach should NOT have immediate response notification in summary mode"
        )

        // Directly invoke fireCoachSummaries — event is within 120min window
        val notificationRepo = application.get<NotificationRepository>()
        val pushService = application.get<PushService>()
        val eventRepository = application.get<EventRepository>()
        runBlocking {
            fireCoachSummaries(notificationRepo, pushService, eventRepository)
        }

        val coachNotifsAfter = getNotifications(coachAuth.token)
        assertTrue(
            coachNotifsAfter.any { it.type == "coach_summary" },
            "Coach should have coach_summary notification after fireCoachSummaries"
        )
    }

    // ------------------------------------------------------------------
    // Absence notification test (NO-07)
    // ------------------------------------------------------------------

    @Test
    fun absence_notifiesCoach() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("absence_notif_coach@example.com", displayName = "Coach Absence")
        val playerAuth = registerAndLogin("absence_notif_player@example.com", displayName = "Player Absence")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        // Coach must be in team_roles to receive team notifications via getTeamMemberIds()
        addCoachToTeam(coachAuth.token, teamId)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val client = createJsonClient()
        client.post("/users/me/abwesenheit") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateAbwesenheitPayloadNotif(
                presetType = "other",
                label = "Holiday",
                ruleType = "period",
                startDate = "2028-07-01",
                endDate = "2028-07-31"
            ))
        }
        delay(300)

        val coachNotifications = getNotifications(coachAuth.token)
        assertTrue(
            coachNotifications.any { it.type == "absence" },
            "Coach should have absence notification when player adds absence rule"
        )
    }

    // ------------------------------------------------------------------
    // Dedup guard test (NO-12)
    // ------------------------------------------------------------------

    @Test
    fun dedup_noDuplicate() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("dedup_coach@example.com", displayName = "Coach Dedup")
        val playerAuth = registerAndLogin("dedup_player@example.com", displayName = "Player Dedup")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        val event = createEvent(coachAuth.token, "Dedup Training", teamIds = listOf(teamId))
        delay(100)

        // Edit event twice within the same hour epoch — same idempotency key
        val client = createJsonClient()
        client.patch("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(EditEventPayloadNotif(scope = "this_only", title = "Dedup Edit 1"))
        }
        delay(100)
        client.patch("/events/${event.id}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(EditEventPayloadNotif(scope = "this_only", title = "Dedup Edit 2"))
        }
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        val editNotifications = notifications.filter { it.type == "event_edit" }
        assertEquals(1, editNotifications.size, "Dedup guard should produce only 1 event_edit notification")
    }

    // ------------------------------------------------------------------
    // Removed-member guard test (NO-12)
    // ------------------------------------------------------------------

    @Test
    fun removedMember_noNotification() = withTeamorgTestApplication {
        val coachAuth = registerAndLogin("removed_coach@example.com", displayName = "Coach Removed")
        val playerAuth = registerAndLogin("removed_player@example.com", displayName = "Player Removed")

        promoteToSuperAdmin(coachAuth.userId)
        val (_, teamId) = setupClubAndTeam(coachAuth.token)
        invitePlayer(coachAuth.token, teamId, playerAuth.token)

        // Remove player from team
        val client = createJsonClient()
        client.delete("/teams/$teamId/members/${playerAuth.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }

        // Create event after removal — player is no longer a team member
        createEvent(coachAuth.token, "Post-removal Event", teamIds = listOf(teamId))
        delay(300)

        val notifications = getNotifications(playerAuth.token)
        assertTrue(
            notifications.none { it.type == "event_new" },
            "Removed player should NOT receive event_new notification"
        )
    }
}
