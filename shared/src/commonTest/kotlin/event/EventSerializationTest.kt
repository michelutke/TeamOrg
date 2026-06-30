package event

import ch.teamorg.domain.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializationTest {
    @Test
    fun `event decodes externalSource and presentCount`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val e = json.decodeFromString<Event>("""{"id":"1","title":"T","type":"training","startAt":"2026-04-27T16:00:00Z","endAt":"2026-04-27T17:30:00Z","status":"active","createdBy":"u","createdAt":"2026-04-27T16:00:00Z","updatedAt":"2026-04-27T16:00:00Z","externalSource":"nds","presentCount":5}""")
        assertEquals("nds", e.externalSource)
        assertEquals(5, e.presentCount)
    }
}
