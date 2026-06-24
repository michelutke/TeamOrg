package ch.teamorg.mail

interface MailService {
    /** True if SMTP is configured and a send was attempted successfully. */
    suspend fun send(
        to: String,
        subject: String,
        plainText: String,
        html: String? = null,
        replyToName: String? = null,
        replyToEmail: String? = null,
        fromName: String = "teamorg"
    ): Boolean
}
