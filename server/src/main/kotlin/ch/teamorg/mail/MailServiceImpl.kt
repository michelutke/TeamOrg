package ch.teamorg.mail

import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory

/**
 * SMTP mail sender wrapping simple-java-mail. Reuses the same `contact.*` SMTP
 * config used by the contact form (SMTP_HOST/PORT/USER/PASS, CONTACT_FROM).
 */
class MailServiceImpl(private val config: ApplicationConfig) : MailService {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun prop(path: String): String =
        config.propertyOrNull(path)?.getString()?.trim().orEmpty()

    private val smtpHost = prop("contact.smtp.host")
    private val smtpPort = prop("contact.smtp.port").toIntOrNull() ?: 587
    private val smtpUser = prop("contact.smtp.user")
    private val smtpPass = prop("contact.smtp.password")
    private val fromAddr = prop("contact.from").ifBlank { smtpUser }

    private val mailer: Mailer? =
        if (smtpHost.isNotBlank() && smtpUser.isNotBlank()) {
            val strategy =
                if (smtpPort == 465) TransportStrategy.SMTPS else TransportStrategy.SMTP_TLS
            MailerBuilder
                .withSMTPServer(smtpHost, smtpPort, smtpUser, smtpPass)
                .withTransportStrategy(strategy)
                // Without explicit timeouts jakarta-mail blocks forever if the SMTP
                // host/port is unreachable, which hangs the request. Fail fast instead.
                .withSessionTimeout(10000)
                .buildMailer()
        } else {
            null
        }

    val isConfigured: Boolean get() = mailer != null
    val from: String get() = fromAddr

    override suspend fun send(
        to: String,
        subject: String,
        plainText: String,
        html: String?,
        replyToName: String?,
        replyToEmail: String?,
        fromName: String
    ): Boolean {
        val activeMailer = mailer
        if (activeMailer == null) {
            logger.error("MailService: SMTP is not configured (contact.smtp.host/user missing)")
            return false
        }

        val builder = EmailBuilder.startingBlank()
            .from(fromName, fromAddr)
            .to(to)
            .withSubject(subject)
            .withPlainText(plainText)
        if (html != null) builder.withHTMLText(html)
        if (replyToEmail != null) builder.withReplyTo(replyToName ?: replyToEmail, replyToEmail)

        withContext(Dispatchers.IO) {
            activeMailer.sendMail(builder.buildEmail())
        }
        return true
    }
}
