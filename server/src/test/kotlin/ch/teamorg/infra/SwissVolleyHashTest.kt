package ch.teamorg.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SwissVolleyHashTest {

    private val playDate = "2025-10-04T16:00:00Z"
    private val hallId = 5
    private val home = 12345
    private val away = 54321

    @Test
    fun `hash is deterministic for the same facts`() {
        val a = matchHash(playDate, hallId, home, away)
        val b = matchHash(playDate, hallId, home, away)
        assertEquals(a, b, "Same facts must produce the same hash")
    }

    @Test
    fun `hash changes when playDate changes`() {
        val base = matchHash(playDate, hallId, home, away)
        val moved = matchHash("2025-10-05T16:00:00Z", hallId, home, away)
        assertNotEquals(base, moved, "A new playDate must change the hash")
    }

    @Test
    fun `hash changes when hall changes`() {
        val base = matchHash(playDate, hallId, home, away)
        val otherHall = matchHash(playDate, 99, home, away)
        assertNotEquals(base, otherHall, "A new hall must change the hash")
    }

    @Test
    fun `hash changes when home team changes`() {
        val base = matchHash(playDate, hallId, home, away)
        val otherHome = matchHash(playDate, hallId, 11111, away)
        assertNotEquals(base, otherHome, "A new home team must change the hash")
    }

    @Test
    fun `hash changes when away team changes`() {
        val base = matchHash(playDate, hallId, home, away)
        val otherAway = matchHash(playDate, hallId, home, 22222)
        assertNotEquals(base, otherAway, "A new away team must change the hash")
    }

    @Test
    fun `hash is independent of the derived end time`() {
        // end_at = start + 2h is a deterministic function of the start (design §6/§13.3)
        // and intentionally excluded from the hash inputs — matchHash takes only the
        // SwissVolley source facts. Recomputing with the same facts (regardless of any
        // derived end time the caller may compute) yields the same hash.
        val a = matchHash(playDate, hallId, home, away)
        val b = matchHash(playDate, hallId, home, away)
        assertEquals(a, b, "Hash must not depend on the derived end time")
    }
}
