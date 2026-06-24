package ch.teamorg.routes

import ch.teamorg.domain.models.NotificationResponse
import ch.teamorg.domain.models.NotificationSettingsResponse
import ch.teamorg.domain.models.ReminderOverrideRequest
import ch.teamorg.domain.models.UpdateNotificationSettingsRequest
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.NotificationRepository
import ch.teamorg.domain.repositories.NotificationRow
import ch.teamorg.domain.repositories.NotificationSettingsRow
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.middleware.requireEventAccess
import ch.teamorg.middleware.requireTeamRole
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
private data class UnreadCountResponse(val count: Long)

@Serializable
private data class MarkAllReadResponse(val marked: Int)

@Serializable
private data class ReminderOverrideResponse(val reminderLeadMinutes: Int?)

private fun NotificationRow.toResponse() = NotificationResponse(
    id = id.toString(),
    type = type,
    title = title,
    body = body,
    entityId = entityId?.toString(),
    entityType = entityType,
    isRead = isRead,
    createdAt = createdAt.toString()
)

private fun NotificationSettingsRow.toResponse() = NotificationSettingsResponse(
    userId = userId.toString(),
    teamId = teamId.toString(),
    eventsNew = eventsNew,
    eventsEdit = eventsEdit,
    eventsCancel = eventsCancel,
    remindersEnabled = remindersEnabled,
    reminderLeadMinutes = reminderLeadMinutes,
    coachResponseMode = coachResponseMode,
    absencesEnabled = absencesEnabled
)

fun Route.notificationRoutes() {
    val notificationRepo by inject<NotificationRepository>()
    val teamRepository by inject<TeamRepository>()
    val eventRepository by inject<EventRepository>()

    authenticate("jwt") {
        get("/notifications") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            val notifications = notificationRepo.getNotifications(userId, limit, offset)
            call.respond(notifications.map { it.toResponse() })
        }

        get("/notifications/unread-count") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val count = notificationRepo.getUnreadCount(userId)
            call.respond(UnreadCountResponse(count))
        }

        post("/notifications/{id}/read") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val notificationId = UUID.fromString(call.parameters["id"])
            val marked = notificationRepo.markRead(userId, notificationId)
            if (marked) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/notifications/read-all") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val count = notificationRepo.markAllRead(userId)
            call.respond(MarkAllReadResponse(count))
        }

        post("/notifications/delete-all") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            notificationRepo.deleteAll(userId)
            call.respond(HttpStatusCode.OK)
        }

        get("/notifications/settings/{teamId}") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
            val settings = notificationRepo.getSettings(userId, teamId)
            if (settings != null) {
                call.respond(settings.toResponse())
            } else {
                call.respond(
                    NotificationSettingsResponse(
                        userId = userId.toString(),
                        teamId = teamId.toString(),
                        eventsNew = true,
                        eventsEdit = true,
                        eventsCancel = true,
                        remindersEnabled = true,
                        reminderLeadMinutes = 120,
                        coachResponseMode = "per_response",
                        absencesEnabled = true
                    )
                )
            }
        }

        put("/notifications/settings/{teamId}") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@put
            val body = call.receive<UpdateNotificationSettingsRequest>()
            notificationRepo.upsertSettings(userId, teamId, body)
            call.respond(HttpStatusCode.OK)
        }

        get("/events/{eventId}/reminder") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val eventId = UUID.fromString(call.parameters["eventId"])
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get
            val leadMinutes = notificationRepo.getReminderOverride(userId, eventId)
            call.respond(ReminderOverrideResponse(leadMinutes))
        }

        put("/events/{eventId}/reminder") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val eventId = UUID.fromString(call.parameters["eventId"])
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@put
            val body = call.receive<ReminderOverrideRequest>()
            notificationRepo.upsertReminderOverride(userId, eventId, body.reminderLeadMinutes)
            call.respond(HttpStatusCode.OK)
        }
    }
}
