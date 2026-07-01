package event

import ch.teamorg.domain.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventSerializationTest {
    @Test
    fun `event decodes externalSource and presentCount`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val e = json.decodeFromString<Event>("""{"id":"1","title":"T","type":"training","startAt":"2026-04-27T16:00:00Z","endAt":"2026-04-27T17:30:00Z","status":"active","createdBy":"u","createdAt":"2026-04-27T16:00:00Z","updatedAt":"2026-04-27T16:00:00Z","externalSource":"nds","presentCount":5}""")
        assertEquals("nds", e.externalSource)
        assertEquals(5, e.presentCount)
    }

    @Test
    fun `event decodes unified-attendance fields with values`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val e = json.decodeFromString<Event>(
            """{"id":"2","title":"Match","type":"match","startAt":"2026-07-01T18:00:00Z","endAt":"2026-07-01T20:00:00Z","status":"active","createdBy":"u","createdAt":"2026-07-01T10:00:00Z","updatedAt":"2026-07-01T10:00:00Z","checkInStatus":"done","checkInCompletedAt":"2026-07-01T19:45:00Z","defaultResponse":"accepted"}"""
        )
        assertEquals("done", e.checkInStatus)
        assertEquals("accepted", e.defaultResponse)
        assertEquals("2026-07-01T19:45:00Z", e.checkInCompletedAt.toString())
    }

    @Test
    fun `event uses defaults when unified-attendance fields absent`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val e = json.decodeFromString<Event>(
            """{"id":"3","title":"Training","type":"training","startAt":"2026-07-01T16:00:00Z","endAt":"2026-07-01T17:30:00Z","status":"active","createdBy":"u","createdAt":"2026-07-01T10:00:00Z","updatedAt":"2026-07-01T10:00:00Z"}"""
        )
        assertEquals("open", e.checkInStatus)
        assertEquals("none", e.defaultResponse)
        assertNull(e.checkInCompletedAt)
    }
}
