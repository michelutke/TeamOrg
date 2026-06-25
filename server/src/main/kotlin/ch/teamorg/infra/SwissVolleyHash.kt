package ch.teamorg.infra

import java.security.MessageDigest

/**
 * Change-detection hash for a synced SwissVolley game.
 *
 * Covers SwissVolley source facts ONLY — playDateUtc, hall, and the two team ids.
 * The derived `end_at` (start + 2h) is intentionally excluded: it is a deterministic
 * function of the start and adds nothing to change detection (design §6 / §13.3).
 */
fun matchHash(playDateUtc: String, hallId: Int?, homeTeamId: Int, awayTeamId: Int): String {
    val source = "$playDateUtc|${hallId ?: ""}|$homeTeamId|$awayTeamId"
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
