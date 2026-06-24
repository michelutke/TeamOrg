package ch.teamorg.routes

import ch.teamorg.domain.repositories.AttendanceRepository
import ch.teamorg.domain.repositories.CheckInRow
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.middleware.requireEventAccess
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant as KInstant
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
private data class CheckInRequest(val status: String, val note: String? = null)

@Serializable
private data class CheckInDto(
    val eventId: String,
    val userId: String,
    val status: String,
    val note: String? = null,
    val setBy: String,
    val setAt: KInstant,
    val previousStatus: String? = null,
    val previousSetBy: String? = null
)

private fun CheckInRow.toDto() = CheckInDto(
    eventId = eventId.toString(),
    userId = userId.toString(),
    status = status,
    note = note,
    setBy = setBy.toString(),
    setAt = KInstant.fromEpochMilliseconds(setAt.toEpochMilli()),
    previousStatus = previousStatus,
    previousSetBy = previousSetBy?.toString()
)

fun Route.checkInRoutes() {
    val attendanceRepo by inject<AttendanceRepository>()
    val teamRepository by inject<TeamRepository>()
    val eventRepository by inject<EventRepository>()

    authenticate("jwt") {
        get("/events/{id}/check-in") {
            val eventId = UUID.fromString(call.parameters["id"])
            // Coach/club_manager of THIS event's team(s) only.
            if (!call.requireEventAccess(eventId, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get

            val entries = attendanceRepo.getCheckInEntries(eventId)
            call.respond(entries)
        }

        put("/events/{id}/check-in/{userId}") {
            val eventId = UUID.fromString(call.parameters["id"])
            val targetUserId = UUID.fromString(call.parameters["userId"])
            val coachId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            // Coach/club_manager of THIS event's team(s) only.
            if (!call.requireEventAccess(eventId, "coach", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@put
            val body = call.receive<CheckInRequest>()

            val record = attendanceRepo.upsertCheckIn(
                eventId = eventId,
                userId = targetUserId,
                status = body.status,
                note = body.note,
                setBy = coachId
            )
            call.respond(record.toDto())
        }
    }
}
