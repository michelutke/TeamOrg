package ch.teamorg.routes

import ch.teamorg.mail.MailService
import ch.teamorg.plugins.RateLimits
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class ContactRequest(
    val club: String,
    val name: String,
    val email: String,
    val members: String = "",
    val message: String
)

@Serializable
data class ContactResponse(val ok: Boolean)

/**
 * Public, unauthenticated endpoint that the marketing site (teamorg.ch) posts demo
 * requests to. Sends an email to the configured inbox (info@teamorg.ch) via SMTP.
 * Protected by an optional shared secret so only the landing site can call it.
 * Configured in application.conf under `contact.*` (env-driven).
 */
fun Route.contactRoutes() {
    val mailService by inject<MailService>()

    val cfg = application.environment.config
    fun prop(path: String): String =
        cfg.propertyOrNull(path)?.getString()?.trim().orEmpty()

    val toAddr = prop("contact.to").ifBlank { "info@teamorg.ch" }
    val sharedSecret = prop("contact.shared-secret")

    rateLimit(RateLimits.CONTACT) {
      post("/contact") {
        // Shared-secret guard (set the same value on the landing site). Compared in
        // constant time so the secret cannot be recovered byte-by-byte from response timing.
        if (sharedSecret.isNotBlank() && !constantTimeEquals(call.request.headers["X-Contact-Secret"], sharedSecret)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        val req = try {
            call.receive<ContactRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ContactResponse(false))
            return@post
        }

        if (req.club.isBlank() || req.name.isBlank() || req.email.isBlank() || req.message.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ContactResponse(false))
            return@post
        }

        // Length caps keep a public endpoint from being used to post megabyte payloads
        // into the support inbox.
        if (req.club.length > MAX_FIELD_LENGTH || req.name.length > MAX_FIELD_LENGTH ||
            req.email.length > MAX_EMAIL_LENGTH || req.members.length > MAX_SHORT_FIELD_LENGTH ||
            req.message.length > MAX_MESSAGE_LENGTH
        ) {
            call.respond(HttpStatusCode.BadRequest, ContactResponse(false))
            return@post
        }

        // The address and name land in Reply-To; a CR or LF there would let a caller
        // append arbitrary SMTP headers (extra recipients, spoofed From).
        if (!isPlausibleEmail(req.email) || containsHeaderBreak(req.name) || containsHeaderBreak(req.club)) {
            call.respond(HttpStatusCode.BadRequest, ContactResponse(false))
            return@post
        }

        val body = buildString {
            appendLine("Neue Demo-Anfrage über teamorg.ch")
            appendLine("=================================")
            appendLine()
            appendLine("Verein:        ${req.club}")
            appendLine("Ansprechperson: ${req.name}")
            appendLine("E-Mail:        ${req.email}")
            if (req.members.isNotBlank()) appendLine("Mitglieder:    ${req.members}")
            appendLine()
            appendLine("Nachricht:")
            appendLine(req.message)
        }

        try {
            val sent = mailService.send(
                to = toAddr,
                subject = "Demo-Anfrage – ${req.club}",
                plainText = body,
                replyToName = req.name,
                replyToEmail = req.email,
                fromName = "teamorg Kontakt"
            )
            if (sent) {
                call.respond(HttpStatusCode.OK, ContactResponse(true))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ContactResponse(false))
            }
        } catch (e: Exception) {
            application.log.error("Contact form: failed to send email", e)
            call.respond(HttpStatusCode.InternalServerError, ContactResponse(false))
        }
      }
    }
}

private const val MAX_FIELD_LENGTH = 200
private const val MAX_SHORT_FIELD_LENGTH = 20
private const val MAX_EMAIL_LENGTH = 254
private const val MAX_MESSAGE_LENGTH = 5_000

private fun containsHeaderBreak(value: String): Boolean =
    value.any { it == '\r' || it == '\n' }

private fun isPlausibleEmail(email: String): Boolean =
    !containsHeaderBreak(email) &&
        email.length <= MAX_EMAIL_LENGTH &&
        email.count { it == '@' } == 1 &&
        email.indexOf('@') > 0 &&
        email.indexOf('@') < email.lastIndexOf('.') &&
        email.lastIndexOf('.') < email.length - 1 &&
        email.none { it.isWhitespace() }

/** Length-independent comparison, so response time reveals nothing about the secret. */
private fun constantTimeEquals(provided: String?, expected: String): Boolean {
    if (provided == null) return false
    val a = provided.toByteArray(Charsets.UTF_8)
    val b = expected.toByteArray(Charsets.UTF_8)
    return java.security.MessageDigest.isEqual(a, b)
}
