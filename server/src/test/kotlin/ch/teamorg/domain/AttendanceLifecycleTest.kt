package ch.teamorg.domain

import ch.teamorg.domain.models.deriveCheckInStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceLifecycleTest {

    private val t0: Instant = Instant.parse("2026-07-01T12:00:00Z")

    /** done: completedAt is set, regardless of other fields */
    @Test
    fun `done when completedAt is not null`() {
        val result = deriveCheckInStatus(
            now = t0,
            cutoff = t0.minusSeconds(3600),   // already past
            endAt = t0.minusSeconds(1800),    // already past
            completedAt = t0.minusSeconds(60) // explicitly done
        )
        assertEquals("done", result)
    }

    /** open: now < cutoff (response_deadline present) */
    @Test
    fun `open when now is before cutoff (deadline set)`() {
        val result = deriveCheckInStatus(
            now = t0,
            cutoff = t0.plusSeconds(3600),  // deadline in future
            endAt = t0.plusSeconds(7200),
            completedAt = null
        )
        assertEquals("open", result)
    }

    /** open: cutoff falls back to startAt when response_deadline is null */
    @Test
    fun `open when now is before startAt (deadline null, cutoff=startAt)`() {
        val result = deriveCheckInStatus(
            now = t0,
            cutoff = t0.plusSeconds(1800),  // simulated startAt used as cutoff
            endAt = t0.plusSeconds(5400),
            completedAt = null
        )
        assertEquals("open", result)
    }

    /** locked: now >= cutoff but now < endAt */
    @Test
    fun `locked when now is between cutoff and endAt`() {
        val result = deriveCheckInStatus(
            now = t0,
            cutoff = t0.minusSeconds(1800), // deadline passed
            endAt = t0.plusSeconds(1800),   // event still running
            completedAt = null
        )
        assertEquals("locked", result)
    }

    /** awaiting_checkin: now >= endAt and no completedAt */
    @Test
    fun `awaiting_checkin when now is after endAt`() {
        val result = deriveCheckInStatus(
            now = t0,
            cutoff = t0.minusSeconds(7200),
            endAt = t0.minusSeconds(3600),
            completedAt = null
        )
        assertEquals("awaiting_checkin", result)
    }
}
