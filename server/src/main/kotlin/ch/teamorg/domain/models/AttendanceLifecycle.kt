package ch.teamorg.domain.models

import java.time.Instant

/**
 * Derives the check-in lifecycle status for an event.
 *
 * @param now       server clock at the moment of evaluation
 * @param cutoff    response_deadline if set, otherwise start_at
 * @param endAt     event end timestamp
 * @param completedAt check_in_completed_at from DB (null if not yet done)
 * @return one of: "done", "open", "locked", "awaiting_checkin"
 */
fun deriveCheckInStatus(
    now: Instant,
    cutoff: Instant,
    endAt: Instant,
    completedAt: Instant?
): String = when {
    completedAt != null  -> "done"
    now < cutoff         -> "open"
    now >= endAt         -> "awaiting_checkin"
    else                 -> "locked"
}
