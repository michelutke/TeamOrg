package ch.teamorg.routes

import ch.teamorg.mail.MailService
import io.ktor.http.*
import io.ktor.server.application.*
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

    post("/contact") {
        // Shared-secret guard (set the same value on the landing site).
        if (sharedSecret.isNotBlank() && call.request.headers["X-Contact-Secret"] != sharedSecret) {
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
