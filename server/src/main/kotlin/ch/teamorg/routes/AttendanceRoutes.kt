package ch.teamorg.routes

import ch.teamorg.domain.repositories.AttendanceRepository
import ch.teamorg.domain.repositories.AttendanceResponseDto
import ch.teamorg.domain.repositories.AttendanceResponseRow
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.NotificationRepository
import ch.teamorg.domain.repositories.RawAttendanceRow
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.middleware.requireEventAccess
import ch.teamorg.middleware.requireTeamRole
import ch.teamorg.infra.NotificationDispatcher
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID
import kotlinx.datetime.Instant as KInstant

private val attnLogger = LoggerFactory.getLogger("AttendanceRoutes")

@Serializable
private data class SubmitResponseRequest(val status: String, val reason: String? = null)

@Serializable
private data class RawAttendanceDto(
    val eventId: String,
    val userId: String,
    val responseStatus: String?,
    val recordStatus: String?,
    val eventStartAt: KInstant
)

private fun AttendanceResponseRow.toDto() = AttendanceResponseDto(
    eventId = eventId.toString(),
    userId = userId.toString(),
    status = status,
    reason = reason,
    abwesenheitRuleId = abwesenheitRuleId?.toString(),
    manualOverride = manualOverride,
    respondedAt = respondedAt?.let { KInstant.fromEpochMilliseconds(it.toEpochMilli()) },
    updatedAt = KInstant.fromEpochMilliseconds(updatedAt.toEpochMilli())
)

private fun RawAttendanceRow.toDto() = RawAttendanceDto(
    eventId = eventId.toString(),
    userId = userId.toString(),
    responseStatus = responseStatus,
    recordStatus = recordStatus,
    eventStartAt = KInstant.fromEpochMilliseconds(eventStartAt.toEpochMilli())
)

fun Route.attendanceRoutes() {
    val attendanceRepo by inject<AttendanceRepository>()
    val dispatcher by inject<NotificationDispatcher>()
    val notificationRepo by inject<NotificationRepository>()
    val eventRepository by inject<EventRepository>()
    val teamRepository by inject<TeamRepository>()

    authenticate("jwt") {
        get("/events/{id}/attendance") {
            val eventId = UUID.fromString(call.parameters["id"])
            // Any member of the event's team(s) may read the roster (the app shows RSVP counts
            // to players); non-members are rejected.
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get
            val responses = attendanceRepo.getEventAttendance(eventId)
            call.respond(responses.map { it.toDto() })
        }

        get("/events/{id}/attendance/me") {
            val eventId = UUID.fromString(call.parameters["id"])
            // You may only view/set your own attendance for events of teams you belong to.
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val response = attendanceRepo.getMyResponse(eventId, userId)
            if (response != null) call.respond(response.toDto())
            else call.respond(HttpStatusCode.NoContent)
        }

        put("/events/{id}/attendance/me") {
            val eventId = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@put
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val body = call.receive<SubmitResponseRequest>()

            if (body.status == "unsure" && body.reason.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Reason required for unsure status")
                return@put
            }

            if (attendanceRepo.isDeadlinePassed(eventId)) {
                call.respond(HttpStatusCode.Conflict, "Response deadline has passed")
                return@put
            }

            val updated = attendanceRepo.upsertResponse(eventId, userId, body.status, body.reason)

            call.application.launch(Dispatchers.IO) {
                try {
                    val event = eventRepository.findById(eventId) ?: return@launch
                    val playerName = "A player"
                    val epoch = java.time.Instant.now().epochSecond / 3600
                    for (teamId in event.teamIds) {
                        val coachIds = notificationRepo.getCoachIdsForTeam(teamId)
                        for (coachId in coachIds) {
                            val settings = notificationRepo.getSettings(coachId, teamId)
                            val mode = settings?.coachResponseMode ?: "per_response"
                            if (mode == "per_response") {
                                notificationRepo.createNotification(
                                    userId = coachId,
                                    type = "response",
                                    title = "RSVP: $playerName",
                                    body = "$playerName ${body.status} for ${event.title}",
                                    entityId = eventId,
                                    entityType = "event",
                                    idempotencyKey = "response:${coachId}:${eventId}:${userId}:${body.status}:$epoch"
                                )
                            }
                            // summary mode coaches are notified via fireCoachSummaries in ReminderSchedulerJob
                        }
                    }
                } catch (e: Exception) {
                    attnLogger.warn("Attendance response notification dispatch failed: ${e.message}")
                }
            }

            call.respond(updated.toDto())
        }

        get("/users/{userId}/attendance") {
            val userId = UUID.fromString(call.parameters["userId"])
            val callerId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)

            if (userId != callerId) {
                // A coach/club_manager may read the attendance of players who share one of
                // their managed teams; everyone else is rejected.
                val callerManagedTeams = teamRepository.getUserTeamRoles(callerId)
                    .filter { it.third == "coach" }
                    .map { it.first }
                    .toSet()
                val callerManagedClubs = teamRepository.getUserClubRoles(callerId)
                    .filter { it.second == "club_manager" }
                    .map { it.first }
                    .toSet()
                val targetRoles = teamRepository.getUserTeamRoles(userId)
                val sharesManagedTeam = targetRoles.any {
                    it.first in callerManagedTeams || it.second in callerManagedClubs
                }
                if (!sharesManagedTeam) {
                    call.respond(HttpStatusCode.Forbidden, "You may not view this user's attendance")
                    return@get
                }
            }

            val from = call.parameters["from"]?.let { Instant.parse(it) }
            val to = call.parameters["to"]?.let { Instant.parse(it) }
            val rows = attendanceRepo.getRawAttendance(userId, from, to)
            call.respond(rows.map { it.toDto() })
        }

        get("/teams/{teamId}/attendance") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            // Restricted to members of the team (a player sees their own team's attendance);
            // club_manager passes via role inheritance.
            if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
            val from = call.parameters["from"]?.let { Instant.parse(it) }
            val to = call.parameters["to"]?.let { Instant.parse(it) }
            val rows = attendanceRepo.getTeamAttendance(teamId, from, to)
            call.respond(rows.map { it.toDto() })
        }
    }
}
