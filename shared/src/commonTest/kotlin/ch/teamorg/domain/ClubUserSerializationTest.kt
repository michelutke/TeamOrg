package ch.teamorg.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClubUserSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ClubUser round-trips through JSON`() {
        val user = ClubUser(
            userId = "u1",
            displayName = "Max Muster",
            email = "max@example.com",
            avatarUrl = "https://example.com/avatar.jpg",
            teamRoles = listOf(TeamRoleRef(teamId = "t1", teamName = "Team A", role = "player"))
        )
        val encoded = json.encodeToString(user)
        val decoded = json.decodeFromString<ClubUser>(encoded)
        assertEquals(user, decoded)
    }

    @Test
    fun `ClubUser defaults avatarUrl to null and teamRoles to empty`() {
        val raw = """{"userId":"u2","displayName":"Anna","email":"anna@example.com"}"""
        val user = json.decodeFromString<ClubUser>(raw)
        assertNull(user.avatarUrl)
        assertEquals(emptyList(), user.teamRoles)
    }

    @Test
    fun `TeamRoleRef round-trips through JSON`() {
        val ref = TeamRoleRef(teamId = "t2", teamName = "Team B", role = "coach")
        val encoded = json.encodeToString(ref)
        val decoded = json.decodeFromString<TeamRoleRef>(encoded)
        assertEquals(ref, decoded)
    }
}
