package ch.teamorg.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder

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
 * requests to. Sends an email to the configured inbox (info@teamorg.ch) via SMTP
 * (Proton Mail). Protected by an optional shared secret so only the landing site can
 * call it. Configured in application.conf under `contact.*` (env-driven).
 */
fun Route.contactRoutes() {
    val cfg = application.environment.config
    fun prop(path: String): String =
        cfg.propertyOrNull(path)?.getString()?.trim().orEmpty()

    val smtpHost = prop("contact.smtp.host")
    val smtpPort = prop("contact.smtp.port").toIntOrNull() ?: 587
    val smtpUser = prop("contact.smtp.user")
    val smtpPass = prop("contact.smtp.password")
    val fromAddr = prop("contact.from").ifBlank { smtpUser }
    val toAddr = prop("contact.to").ifBlank { "info@teamorg.ch" }
    val sharedSecret = prop("contact.shared-secret")

    val mailer: Mailer? =
        if (smtpHost.isNotBlank() && smtpUser.isNotBlank()) {
            val strategy =
                if (smtpPort == 465) TransportStrategy.SMTPS else TransportStrategy.SMTP_TLS
            MailerBuilder
                .withSMTPServer(smtpHost, smtpPort, smtpUser, smtpPass)
                .withTransportStrategy(strategy)
                .buildMailer()
        } else {
            null
        }

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

        if (mailer == null) {
            application.log.error("Contact form: SMTP is not configured (contact.smtp.host/user missing)")
            call.respond(HttpStatusCode.InternalServerError, ContactResponse(false))
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
            val email = EmailBuilder.startingBlank()
                .from("teamorg Kontakt", fromAddr)
                .to(toAddr)
                .withReplyTo(req.name, req.email)
                .withSubject("Demo-Anfrage – ${req.club}")
                .withPlainText(body)
                .buildEmail()
            withContext(Dispatchers.IO) {
                mailer.sendMail(email)
            }
            call.respond(HttpStatusCode.OK, ContactResponse(true))
        } catch (e: Exception) {
            application.log.error("Contact form: failed to send email", e)
            call.respond(HttpStatusCode.InternalServerError, ContactResponse(false))
        }
    }
}
