package ch.teamorg.nds

import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.infra.nds.MatchCandidateUser
import ch.teamorg.infra.nds.NdsMemberMatcher
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NdsMemberMatcherTest {

    private fun row(
        lastName: String,
        firstName: String,
        birthDate: LocalDate? = null,
        funktion: String = "Teilnehmer/in"
    ) = NdsMemberInput(lastName = lastName, firstName = firstName, birthDate = birthDate, personNumber = null, funktion = funktion)

    private fun candidate(
        firstName: String,
        lastName: String,
        birthDate: LocalDate? = null,
        linkedNdsIdentity: Triple<String, String, LocalDate?>? = null,
        userId: UUID = UUID.randomUUID()
    ) = MatchCandidateUser(
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        birthDate = birthDate,
        linkedNdsIdentity = linkedNdsIdentity
    )

    @Test
    fun `exact name match yields single HIGH candidate and preselection`() {
        val user = candidate(firstName = "Lara", lastName = "Müller")
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Müller", firstName = "Lara")),
            teamUsers = listOf(user)
        )

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(1, suggestion.candidates.size)
        assertEquals(user.userId, suggestion.candidates[0].userId)
        assertEquals("HIGH", suggestion.candidates[0].score)
        assertEquals(user.userId, suggestion.preselectedUserId)
        assertNull(suggestion.alreadyLinkedUserId)
    }

    @Test
    fun `umlaut variant is an exact match after normalization`() {
        val user = candidate(firstName = "Lara", lastName = "Luethi")
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Lüthi", firstName = "Lara")),
            teamUsers = listOf(user)
        )

        val suggestion = result[0]
        assertEquals(1, suggestion.candidates.size)
        assertEquals("HIGH", suggestion.candidates[0].score)
        assertEquals(user.userId, suggestion.preselectedUserId)
    }

    @Test
    fun `one character typo on one name with the other exact yields MEDIUM and no preselection`() {
        val user = candidate(firstName = "Tim", lastName = "Meyer")
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Meier", firstName = "Tim")),
            teamUsers = listOf(user)
        )

        val suggestion = result[0]
        assertEquals(1, suggestion.candidates.size)
        assertEquals("MEDIUM", suggestion.candidates[0].score)
        assertNull(suggestion.preselectedUserId)
    }

    @Test
    fun `typo plus matching birthdate upgrades to HIGH and sets birthdateMatch`() {
        val birthDate = LocalDate.of(2009, 1, 15)
        val user = candidate(firstName = "Tim", lastName = "Meyer", birthDate = birthDate)
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Meier", firstName = "Tim", birthDate = birthDate)),
            teamUsers = listOf(user)
        )

        val suggestion = result[0]
        assertEquals(1, suggestion.candidates.size)
        val candidateResult = suggestion.candidates[0]
        assertEquals("HIGH", candidateResult.score)
        assertTrue(candidateResult.birthdateMatch)
        assertEquals(user.userId, suggestion.preselectedUserId)
    }

    @Test
    fun `two users with identical normalized name are both HIGH and ambiguous`() {
        val userA = candidate(firstName = "Tim", lastName = "Meier")
        val userB = candidate(firstName = "Tim", lastName = "Meier")
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Meier", firstName = "Tim")),
            teamUsers = listOf(userA, userB)
        )

        val suggestion = result[0]
        assertEquals(2, suggestion.candidates.size)
        assertTrue(suggestion.candidates.all { it.score == "HIGH" })
        assertNull(suggestion.preselectedUserId)
    }

    @Test
    fun `row matching a candidate's linked nds identity is a locked row`() {
        val birthDate = LocalDate.of(2008, 5, 20)
        val linked = candidate(
            firstName = "Lara",
            lastName = "Müller",
            linkedNdsIdentity = Triple("Müller", "Lara", birthDate)
        )
        val result = NdsMemberMatcher.suggest(
            rows = listOf(row(lastName = "Müller", firstName = "Lara", birthDate = birthDate)),
            teamUsers = listOf(linked)
        )

        val suggestion = result[0]
        assertEquals(linked.userId, suggestion.alreadyLinkedUserId)
        assertTrue(suggestion.candidates.isEmpty())
        assertNull(suggestion.preselectedUserId)
    }

    @Test
    fun `rowKey is stable and section-prefixed`() {
        val leaderKey1 = NdsMemberMatcher.rowKey("Leiter/in", "Müller", "Lara")
        val leaderKey2 = NdsMemberMatcher.rowKey("Leiter/in", "Müller", "Lara")
        val participantKey = NdsMemberMatcher.rowKey("Teilnehmer/in", "Müller", "Lara")

        assertEquals(leaderKey1, leaderKey2)
        assertTrue(leaderKey1.startsWith("L:"))
        assertTrue(participantKey.startsWith("T:"))
        assertEquals("L:mueller|lara", leaderKey1)
        assertEquals("T:mueller|lara", participantKey)
    }
}
