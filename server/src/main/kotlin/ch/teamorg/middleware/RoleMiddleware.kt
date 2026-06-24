package ch.teamorg.middleware

import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.TeamRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*

suspend fun ApplicationCall.requireClubRole(clubId: UUID, role: String, clubRepository: ClubRepository): Boolean {
    val principal = principal<JWTPrincipal>()
    val userId = principal?.payload?.subject?.let { UUID.fromString(it) } ?: return false

    val hasRole = clubRepository.hasRole(userId, clubId, role)
    if (!hasRole) {
        respond(HttpStatusCode.Forbidden, "You do not have the required role for this club")
        return false
    }
    return true
}

suspend fun ApplicationCall.requireTeamRole(teamId: UUID, vararg roles: String, teamRepository: TeamRepository): Boolean {
    val principal = principal<JWTPrincipal>()
    val userId = principal?.payload?.subject?.let { UUID.fromString(it) } ?: return false

    val hasRole = teamRepository.hasRole(userId, teamId, *roles)
    if (!hasRole) {
        respond(HttpStatusCode.Forbidden, "You do not have the required role for this team")
        return false
    }
    return true
}

/**
 * Verify the caller is a member of the club: either a club_manager of the club, or holds any
 * team role in one of the club's teams. Used to scope club-level reads.
 */
suspend fun ApplicationCall.requireClubMember(clubId: UUID, clubRepository: ClubRepository): Boolean {
    val principal = principal<JWTPrincipal>()
    val userId = principal?.payload?.subject?.let { UUID.fromString(it) } ?: return false

    if (!clubRepository.isMember(userId, clubId)) {
        respond(HttpStatusCode.Forbidden, "You are not a member of this club")
        return false
    }
    return true
}

/**
 * Verify the caller may access an event. The event is resolved to its team(s); the caller must
 * hold one of [roles] in at least one of those teams. Events with no teams (personal/orphaned)
 * are only accessible to their creator.
 *
 * Returns true when access is granted. On failure responds (404 if event missing, 403 otherwise)
 * and returns false.
 */
suspend fun ApplicationCall.requireEventAccess(
    eventId: UUID,
    vararg roles: String,
    eventRepository: EventRepository,
    teamRepository: TeamRepository
): Boolean {
    val principal = principal<JWTPrincipal>()
    val userId = principal?.payload?.subject?.let { UUID.fromString(it) } ?: return false

    val event = eventRepository.findById(eventId)
    if (event == null) {
        respond(HttpStatusCode.NotFound)
        return false
    }

    if (event.teamIds.isEmpty()) {
        // Personal/orphaned event: only the creator may access it.
        if (event.createdBy == userId) return true
        respond(HttpStatusCode.Forbidden, "You do not have access to this event")
        return false
    }

    for (teamId in event.teamIds) {
        if (teamRepository.hasRole(userId, teamId, *roles)) return true
    }
    respond(HttpStatusCode.Forbidden, "You do not have access to this event")
    return false
}
