package ch.teamorg.mail

/** Bilingual (German, then English) password reset email. */
fun buildPasswordResetEmail(resetUrl: String): InviteEmailContent {
    val subject = "Passwort zurücksetzen / Reset your password"

    val plainText = buildString {
        appendLine("Hallo,")
        appendLine()
        appendLine("für dein teamorg-Konto wurde ein neues Passwort angefordert. Über diesen Link kannst du ein neues Passwort setzen:")
        appendLine(resetUrl)
        appendLine()
        appendLine("Der Link ist 1 Stunde gültig. Wenn du das nicht angefordert hast, kannst du diese E-Mail ignorieren.")
        appendLine()
        appendLine("Viele Grüße")
        appendLine("Dein teamorg-Team")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("Hello,")
        appendLine()
        appendLine("a new password was requested for your teamorg account. Use this link to set a new password:")
        appendLine(resetUrl)
        appendLine()
        appendLine("The link is valid for 1 hour. If you did not request this, you can ignore this email.")
        appendLine()
        appendLine("Best regards")
        appendLine("Your teamorg team")
    }

    val html = """
        <p>Hallo,</p>
        <p>für dein teamorg-Konto wurde ein neues Passwort angefordert.</p>
        <p><a href="$resetUrl">Neues Passwort setzen</a></p>
        <p>Der Link ist 1 Stunde gültig. Wenn du das nicht angefordert hast, kannst du diese E-Mail ignorieren.</p>
        <p>Viele Grüße<br>Dein teamorg-Team</p>
        <hr>
        <p>Hello,</p>
        <p>a new password was requested for your teamorg account.</p>
        <p><a href="$resetUrl">Set a new password</a></p>
        <p>The link is valid for 1 hour. If you did not request this, you can ignore this email.</p>
        <p>Best regards<br>Your teamorg team</p>
    """.trimIndent()

    return InviteEmailContent(subject, plainText, html)
}
