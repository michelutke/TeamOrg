package ch.teamorg.routes

import ch.teamorg.domain.models.CreateEventRequest
import ch.teamorg.domain.models.EditEventRequest
import ch.teamorg.domain.models.RecurringScope
import ch.teamorg.domain.repositories.AttendanceRepository
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.NotificationRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.middleware.requireEventAccess
import ch.teamorg.middleware.requireTeamRole
import ch.teamorg.infra.AbwesenheitBackfillJob
import ch.teamorg.infra.NotificationDispatcher
import org.slf4j.LoggerFactory
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID

@Serializable
private data class EditEventWithScope(
    val scope: String? = "this_only",
    val title: String? = null,
    val type: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val meetupAt: String? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val teamIds: List<String>? = null,
    val subgroupIds: List<String>? = null,
    val defaultResponse: String? = null
)

private val VALID_DEFAULT_RESPONSES = setOf("none", "accepted", "declined")

@Serializable
private data class CancelScopeRequest(val scope: String? = "this_only")

@Serializable
private data class ReconcileRequest(
    val meetupAt: String? = null,
    val notes: String? = null,
    val minAttendees: Int? = null,
    val resetAvailability: Boolean = false
)

private val routeLogger = LoggerFactory.getLogger("EventRoutes")

private fun formatDate(instant: Instant): String {
    val zdt = instant.atZone(java.time.ZoneId.of("UTC"))
    return "${zdt.dayOfMonth} ${zdt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${zdt.year}"
}

fun Route.eventRoutes() {
    val eventRepository by inject<EventRepository>()
    val teamRepository by inject<TeamRepository>()
    val backfillJob by inject<AbwesenheitBackfillJob>()
    val dispatcher by inject<NotificationDispatcher>()
    val notificationRepo by inject<NotificationRepository>()
    val attendanceRepository by inject<AttendanceRepository>()

    authenticate("jwt") {
        get("/users/me/events") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val from = call.parameters["from"]?.let { Instant.parse(it) }
            val to = call.parameters["to"]?.let { Instant.parse(it) }
            val type = call.parameters["type"]
            val teamId = call.parameters["teamId"]?.let { UUID.fromString(it) }
            val events = eventRepository.findEventsForUser(userId, from, to, type, teamId)
            call.respond(events)
        }

        get("/users/me/events/awaiting-checkin") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val events = eventRepository.listAwaitingCheckInForUser(userId)
            call.respond(events)
        }

        get("/teams/{teamId}/events") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
            val from = call.parameters["from"]?.let { Instant.parse(it) }
            val to = call.parameters["to"]?.let { Instant.parse(it) }
            val events = eventRepository.findEventsForTeam(teamId, from, to)
            call.respond(events)
        }

        get("/events/{id}") {
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get
            val event = eventRepository.findByIdWithTeams(id)
            if (event != null) call.respond(event)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/events") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val request = call.receive<CreateEventRequest>()
            if (request.defaultResponse !in VALID_DEFAULT_RESPONSES) {
                call.respond(HttpStatusCode.BadRequest, "Invalid defaultResponse")
                return@post
            }
            // An event may only be created for teams the caller coaches/manages.
            for (teamId in request.teamIds) {
                if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
            }
            val event = eventRepository.create(request, userId)
            backfillJob.applyRulesToNewEvent(event.id, event.startAt, event.teamIds)

            call.application.launch(Dispatchers.IO) {
                try {
                    for (teamId in event.teamIds) {
                        dispatcher.notifyTeamMembers(
                            teamId = teamId,
                            excludeUserId = userId,
                            type = "event_new",
                            title = "New Event",
                            body = "${event.title} on ${formatDate(event.startAt)}",
                            entityId = event.id,
                            entityType = "event",
                            idempotencyKeySuffix = "create"
                        )
                        val memberIds = notificationRepo.getTeamMemberIds(teamId)
                        val userIdToFireAt = memberIds.associateWith { memberId ->
                            val settings = notificationRepo.getSettings(memberId, teamId)
                            val leadMinutes = notificationRepo.getReminderOverride(memberId, event.id)
                                ?: settings?.reminderLeadMinutes
                                ?: 120
                            event.startAt.minusSeconds(leadMinutes.toLong() * 60)
                        }
                        notificationRepo.insertReminderRows(event.id, userIdToFireAt)
                    }
                } catch (e: Exception) {
                    routeLogger.warn("Event create notification dispatch failed: ${e.message}")
                }
            }

            call.respond(HttpStatusCode.Created, event)
        }

        patch("/events/{id}") {
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@patch
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val body = call.receive<EditEventWithScope>()
            if (body.defaultResponse != null && body.defaultResponse !in VALID_DEFAULT_RESPONSES) {
                call.respond(HttpStatusCode.BadRequest, "Invalid defaultResponse")
                return@patch
            }
            val scope = RecurringScope.valueOf(body.scope ?: "this_only")
            val editRequest = EditEventRequest(
                title = body.title,
                type = body.type,
                startAt = body.startAt?.let { Instant.parse(it) },
                endAt = body.endAt?.let { Instant.parse(it) },
                meetupAt = body.meetupAt?.let { Instant.parse(it) },
                location = body.location,
                description = body.description,
                minAttendees = body.minAttendees,
                teamIds = body.teamIds?.map { UUID.fromString(it) },
                subgroupIds = body.subgroupIds?.map { UUID.fromString(it) },
                defaultResponse = body.defaultResponse
            )

            val existing = eventRepository.findById(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            when (scope) {
                RecurringScope.this_only -> {
                    eventRepository.update(id, editRequest)
                }
                RecurringScope.this_and_future -> {
                    if (existing.seriesId != null && existing.seriesSequence != null) {
                        // Update current event + all future events in the series
                        eventRepository.update(id, editRequest)
                        eventRepository.updateFutureInSeries(
                            existing.seriesId, existing.seriesSequence + 1, editRequest
                        )
                        eventRepository.updateSeriesTemplate(existing.seriesId, editRequest)
                    } else {
                        eventRepository.update(id, editRequest)
                    }
                }
                RecurringScope.all -> {
                    if (existing.seriesId != null) {
                        eventRepository.updateSeriesTemplate(existing.seriesId, editRequest)
                        eventRepository.updateFutureInSeries(
                            existing.seriesId, existing.seriesSequence ?: 0, editRequest
                        )
                    } else {
                        eventRepository.update(id, editRequest)
                    }
                }
            }

            val updated = eventRepository.findByIdWithTeams(id)
            if (updated != null) {
                call.application.launch(Dispatchers.IO) {
                    try {
                        for (teamId in updated.event.teamIds) {
                            dispatcher.notifyTeamMembers(
                                teamId = teamId,
                                excludeUserId = userId,
                                type = "event_edit",
                                title = "Event Updated",
                                body = "${updated.event.title} has been updated",
                                entityId = updated.event.id,
                                entityType = "event",
                                idempotencyKeySuffix = "edit"
                            )
                        }
                        notificationRepo.deleteReminderRowsForEvent(id)
                        for (teamId in updated.event.teamIds) {
                            val memberIds = notificationRepo.getTeamMemberIds(teamId)
                            val userIdToFireAt = memberIds.associateWith { memberId ->
                                val settings = notificationRepo.getSettings(memberId, teamId)
                                val leadMinutes = notificationRepo.getReminderOverride(memberId, id)
                                    ?: settings?.reminderLeadMinutes
                                    ?: 120
                                updated.event.startAt.minusSeconds(leadMinutes.toLong() * 60)
                            }
                            notificationRepo.insertReminderRows(id, userIdToFireAt)
                        }
                    } catch (e: Exception) {
                        routeLogger.warn("Event edit notification dispatch failed: ${e.message}")
                    }
                }
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/events/{id}/cancel") {
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@post
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val body = call.receive<CancelScopeRequest>()
            val scope = RecurringScope.valueOf(body.scope ?: "this_only")

            val existing = eventRepository.findById(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            when (scope) {
                RecurringScope.this_only -> eventRepository.cancel(id)
                RecurringScope.this_and_future -> {
                    if (existing.seriesId != null && existing.seriesSequence != null) {
                        eventRepository.cancel(id)
                        eventRepository.cancelFutureInSeries(existing.seriesId, existing.seriesSequence + 1)
                    } else {
                        eventRepository.cancel(id)
                    }
                }
                RecurringScope.all -> {
                    if (existing.seriesId != null) {
                        eventRepository.cancelFutureInSeries(existing.seriesId, 0)
                    }
                    eventRepository.cancel(id)
                }
            }

            call.application.launch(Dispatchers.IO) {
                try {
                    for (teamId in existing.teamIds) {
                        dispatcher.notifyTeamMembers(
                            teamId = teamId,
                            excludeUserId = userId,
                            type = "event_cancel",
                            title = "Event Cancelled",
                            body = "${existing.title} has been cancelled",
                            entityId = id,
                            entityType = "event",
                            idempotencyKeySuffix = "cancel"
                        )
                    }
                    notificationRepo.deleteReminderRowsForEvent(id)
                } catch (e: Exception) {
                    routeLogger.warn("Event cancel notification dispatch failed: ${e.message}")
                }
            }  // end of cancel notification launch

            call.respond(HttpStatusCode.OK)
        }

        post("/events/{id}/uncancel") {
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@post
            val body = call.receive<CancelScopeRequest>()
            val scope = RecurringScope.valueOf(body.scope ?: "this_only")

            val existing = eventRepository.findById(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            when (scope) {
                RecurringScope.this_only -> eventRepository.uncancel(id)
                RecurringScope.this_and_future -> {
                    if (existing.seriesId != null && existing.seriesSequence != null) {
                        eventRepository.uncancel(id)
                        eventRepository.uncancelFutureInSeries(existing.seriesId, existing.seriesSequence + 1)
                    } else {
                        eventRepository.uncancel(id)
                    }
                }
                RecurringScope.all -> {
                    if (existing.seriesId != null) {
                        eventRepository.uncancelFutureInSeries(existing.seriesId, 0)
                    }
                    eventRepository.uncancel(id)
                }
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/events/{id}/duplicate") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@post
            val event = eventRepository.duplicate(id, userId)
            if (event != null) {
                backfillJob.applyRulesToNewEvent(event.id, event.startAt, event.teamIds)
                call.respond(HttpStatusCode.Created, event)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/events/{id}/reconcile") {
            val id = UUID.fromString(call.parameters["id"])
            if (!call.requireEventAccess(id, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@post
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)

            val existing = eventRepository.findById(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (existing.externalSource != "swissvolley" || !existing.needsReview) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }

            val body = call.receive<ReconcileRequest>()
            // Only coach-owned fields. SV-owned facts (title/start/end/location) are never touched.
            val editRequest = EditEventRequest(
                title = null,
                type = null,
                startAt = null,
                endAt = null,
                meetupAt = body.meetupAt?.let { Instant.parse(it) },
                location = null,
                description = body.notes,
                minAttendees = body.minAttendees,
                teamIds = null,
                subgroupIds = null
            )
            eventRepository.update(id, editRequest)
            eventRepository.clearNeedsReview(id)

            val updated = eventRepository.findByIdWithTeams(id)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (body.resetAvailability) {
                attendanceRepository.resetResponsesForEvent(id)
                call.application.launch(Dispatchers.IO) {
                    try {
                        for (teamId in updated.event.teamIds) {
                            dispatcher.notifyTeamMembers(
                                teamId = teamId,
                                excludeUserId = userId,
                                type = "event_edit",
                                title = "Event Updated",
                                body = "${updated.event.title} has been updated",
                                entityId = updated.event.id,
                                entityType = "event",
                                idempotencyKeySuffix = "edit"
                            )
                        }
                        notificationRepo.deleteReminderRowsForEvent(id)
                        for (teamId in updated.event.teamIds) {
                            val memberIds = notificationRepo.getTeamMemberIds(teamId)
                            val userIdToFireAt = memberIds.associateWith { memberId ->
                                val settings = notificationRepo.getSettings(memberId, teamId)
                                val leadMinutes = notificationRepo.getReminderOverride(memberId, id)
                                    ?: settings?.reminderLeadMinutes
                                    ?: 120
                                updated.event.startAt.minusSeconds(leadMinutes.toLong() * 60)
                            }
                            notificationRepo.insertReminderRows(id, userIdToFireAt)
                        }
                    } catch (e: Exception) {
                        routeLogger.warn("Event reconcile notification dispatch failed: ${e.message}")
                    }
                }
            }

            call.respond(updated)
        }
    }
}
