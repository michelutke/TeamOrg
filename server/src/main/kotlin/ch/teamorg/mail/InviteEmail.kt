package ch.teamorg.mail

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun roleLabelDe(role: String): String = when (role) {
    "player" -> "Spieler"
    "coach" -> "Trainer"
    "club_manager" -> "Club-Manager"
    else -> role
}

private val DATE_DE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Zurich"))

// expiresAt is an ISO-8601 instant string; render a readable Swiss date for the email.
private fun formatExpiry(expiresAt: String): String =
    runCatching { DATE_DE.format(Instant.parse(expiresAt)) }.getOrDefault(expiresAt)

data class InviteEmailContent(
    val subject: String,
    val plainText: String,
    val html: String
)

/**
 * Builds the German invite email. [teamName] null → club-scoped invite.
 */
fun buildInviteEmail(
    inviterName: String,
    teamName: String?,
    clubName: String,
    role: String,
    inviteUrl: String,
    expiresAt: String
): InviteEmailContent {
    val label = roleLabelDe(role)
    val expiry = formatExpiry(expiresAt)
    val subject = if (teamName != null) {
        "Einladung ins Team $teamName bei teamorg"
    } else {
        "Einladung als Club-Manager bei $clubName"
    }

    val intro = if (teamName != null) {
        "$inviterName hat dich als $label ins Team \"$teamName\" ($clubName) bei teamorg eingeladen."
    } else {
        "$inviterName hat dich als $label beim Verein \"$clubName\" bei teamorg eingeladen."
    }

    val plainText = buildString {
        appendLine("Hallo,")
        appendLine()
        appendLine(intro)
        appendLine()
        appendLine("Einladung annehmen:")
        appendLine(inviteUrl)
        appendLine()
        appendLine("Gültig bis: $expiry")
        appendLine()
        appendLine("Viele Grüße")
        appendLine("Dein teamorg-Team")
    }

    val html = """
        <p>Hallo,</p>
        <p>$intro</p>
        <p><a href="$inviteUrl">Einladung annehmen</a></p>
        <p>Gültig bis: $expiry</p>
        <p>Viele Grüße<br>Dein teamorg-Team</p>
    """.trimIndent()

    return InviteEmailContent(subject, plainText, html)
}
