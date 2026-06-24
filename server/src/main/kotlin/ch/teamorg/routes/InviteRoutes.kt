package ch.teamorg.routes

import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.InviteRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.mail.MailService
import ch.teamorg.mail.buildInviteEmail
import ch.teamorg.middleware.authenticateUser
import ch.teamorg.middleware.requireClubRole
import ch.teamorg.middleware.requireTeamRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.*

// Single RFC822-ish address only: no whitespace/CR/LF, no commas, no angle brackets or
// other separators — blocks header injection and multi-recipient fan-out via the invite endpoint.
private val EMAIL_REGEX = Regex("^[^\\s@,;:<>\"]+@[^\\s@,;:<>\"]+\\.[^\\s@,;:<>\"]+$")

/** Trims + lowercases and returns the address only if it is a single valid address, else null. */
private fun normalizeEmail(raw: String): String? {
    val e = raw.trim().lowercase()
    return if (e.length <= 254 && EMAIL_REGEX.matches(e)) e else null
}

@Serializable
data class CreateInviteRequest(
    val role: String,
    val email: String? = null,
    val reusable: Boolean = false,
    val expiresInDays: Int? = null
)

@Serializable
data class CreateClubInviteRequest(
    val role: String,
    val email: String
)

@Serializable
data class InviteResponse(val token: String, val inviteUrl: String, val expiresAt: String)

@Serializable
data class SetActiveRequest(val active: Boolean)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class EmailMismatchResponse(val error: String = "email_mismatch", val invitedEmail: String)

fun Route.inviteRoutes() {
    val inviteRepository by inject<InviteRepository>()
    val teamRepository by inject<TeamRepository>()
    val clubRepository by inject<ClubRepository>()
    val userRepository by inject<UserRepository>()
    val mailService by inject<MailService>()

    val inviteBaseUrl = application.environment.config
        .propertyOrNull("invite.base-url")?.getString()?.trim()
        ?.ifBlank { null } ?: "https://teamorg.ch"

    fun inviteUrlFor(token: String): String = "$inviteBaseUrl/i/$token"

    route("/teams/{teamId}/invites") {
        authenticate("jwt") {
            post {
                val teamId = call.parameters["teamId"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid team ID")

                if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) {
                    return@post
                }

                val request = call.receive<CreateInviteRequest>()
                if (request.role !in listOf("player", "coach")) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid role. Must be 'player' or 'coach'")
                }

                val rawEmail = request.email?.takeIf { it.isNotBlank() }
                val normalizedEmail = if (rawEmail != null) {
                    normalizeEmail(rawEmail)
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                } else null

                val hasEmail = normalizedEmail != null
                if (request.reusable) {
                    if (request.role != "player" || hasEmail) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Reusable invites must have role 'player' and no email"
                        )
                    }
                }

                call.authenticateUser(userRepository) { user ->
                    val invite = inviteRepository.create(
                        teamId = teamId,
                        createdByUserId = UUID.fromString(user.id),
                        role = request.role,
                        email = normalizedEmail,
                        reusable = request.reusable,
                        expiresInDays = request.expiresInDays
                    )

                    if (normalizedEmail != null) {
                        runCatching {
                            val details = inviteRepository.getInviteDetails(invite.token)
                            if (details != null) {
                                val email = buildInviteEmail(
                                    inviterName = user.displayName,
                                    teamName = details.teamName,
                                    clubName = details.clubName,
                                    role = invite.role,
                                    inviteUrl = inviteUrlFor(invite.token),
                                    expiresAt = invite.expiresAt
                                )
                                mailService.send(
                                    to = normalizedEmail,
                                    subject = email.subject,
                                    plainText = email.plainText,
                                    html = email.html,
                                    replyToName = user.displayName,
                                    replyToEmail = user.email
                                )
                            } else {
                                application.log.warn("Invite email skipped: details not found for ${invite.token}")
                            }
                        }.onFailure { application.log.error("Invite email send failed", it) }
                    }

                    call.respond(
                        HttpStatusCode.Created,
                        InviteResponse(invite.token, inviteUrlFor(invite.token), invite.expiresAt)
                    )
                }
            }
        }
    }

    route("/clubs/{clubId}/invites") {
        authenticate("jwt") {
            post {
                val clubId = call.parameters["clubId"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid club ID")

                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) {
                    return@post
                }

                val request = call.receive<CreateClubInviteRequest>()
                if (request.role != "club_manager") {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid role. Must be 'club_manager'")
                }
                val normalizedEmail = normalizeEmail(request.email)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid email address")

                call.authenticateUser(userRepository) { user ->
                    val invite = inviteRepository.createClubInvite(
                        clubId = clubId,
                        createdByUserId = UUID.fromString(user.id),
                        role = request.role,
                        email = normalizedEmail
                    )

                    runCatching {
                        val details = inviteRepository.getInviteDetails(invite.token)
                        if (details != null) {
                            val email = buildInviteEmail(
                                inviterName = user.displayName,
                                teamName = null,
                                clubName = details.clubName,
                                role = invite.role,
                                inviteUrl = inviteUrlFor(invite.token),
                                expiresAt = invite.expiresAt
                            )
                            mailService.send(
                                to = normalizedEmail,
                                subject = email.subject,
                                plainText = email.plainText,
                                html = email.html,
                                replyToName = user.displayName,
                                replyToEmail = user.email
                            )
                        } else {
                            application.log.warn("Club invite email skipped: details not found for ${invite.token}")
                        }
                    }.onFailure { application.log.error("Club invite email send failed", it) }

                    call.respond(
                        HttpStatusCode.Created,
                        InviteResponse(invite.token, inviteUrlFor(invite.token), invite.expiresAt)
                    )
                }
            }
        }
    }

    route("/invites/{token}") {
        // GET details - public, no auth
        get {
            val token = call.parameters["token"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing token")
            val details = inviteRepository.getInviteDetails(token)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Invite not found")
            call.respond(details)
        }

        authenticate("jwt") {
            // PATCH active - role guard depends on scope
            patch {
                val token = call.parameters["token"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing token")
                val invite = inviteRepository.findByToken(token)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))

                val authorized = if (invite.clubId != null) {
                    call.requireClubRole(UUID.fromString(invite.clubId), "club_manager", clubRepository)
                } else {
                    call.requireTeamRole(
                        UUID.fromString(invite.teamId!!),
                        "coach", "club_manager",
                        teamRepository = teamRepository
                    )
                }
                if (!authorized) return@patch

                val request = call.receive<SetActiveRequest>()
                inviteRepository.setActive(token, request.active)
                call.respond(HttpStatusCode.OK, OkResponse())
            }

            // POST redeem - auth required
            post("/redeem") {
                val token = call.parameters["token"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing token")

                // 1. not found
                val invite = inviteRepository.findByToken(token)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))

                // 2. inactive
                if (!invite.active) {
                    return@post call.respond(HttpStatusCode.Gone, ErrorResponse("inactive"))
                }

                // 3. expired
                if (Instant.now().isAfter(Instant.parse(invite.expiresAt))) {
                    return@post call.respond(HttpStatusCode.Gone, ErrorResponse("expired"))
                }

                call.authenticateUser(userRepository) { user ->
                    val userId = UUID.fromString(user.id)

                    // 4. email mismatch (personal invites only)
                    val invitedEmail = invite.invitedEmail
                    if (invitedEmail != null) {
                        if (invitedEmail.lowercase().trim() != user.email.lowercase().trim()) {
                            return@authenticateUser call.respond(
                                HttpStatusCode.Forbidden,
                                EmailMismatchResponse(invitedEmail = invitedEmail)
                            )
                        }
                    }

                    // 5 & 6. member-check + insert (handled in repo, returns outcome)
                    inviteRepository.redeem(invite, userId)
                    call.respond(HttpStatusCode.OK, OkResponse())
                }
            }
        }
    }
}
