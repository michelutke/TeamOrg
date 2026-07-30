package ch.teamorg.infra.nds

import ch.teamorg.domain.models.NdsMemberInput
import java.time.LocalDate
import java.util.UUID

data class MatchCandidateUser(
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate?,
    val linkedNdsIdentity: Triple<String, String, LocalDate?>?
)

data class MemberSuggestion(
    val rowKey: String,
    val candidates: List<Candidate>,
    val preselectedUserId: UUID?,
    val alreadyLinkedUserId: UUID?
) {
    data class Candidate(val userId: UUID, val displayName: String, val score: String, val birthdateMatch: Boolean)
}

object NdsMemberMatcher {

    private const val HIGH = "HIGH"
    private const val MEDIUM = "MEDIUM"
    private const val MAX_CANDIDATES = 5

    fun rowKey(funktion: String, lastName: String, firstName: String): String {
        val prefix = if (funktion.contains("leiter", ignoreCase = true)) "L:" else "T:"
        return "$prefix${normalize(lastName)}|${normalize(firstName)}"
    }

    fun suggest(rows: List<NdsMemberInput>, teamUsers: List<MatchCandidateUser>): List<MemberSuggestion> =
        rows.map { row -> suggestForRow(row, teamUsers) }

    private fun suggestForRow(row: NdsMemberInput, teamUsers: List<MatchCandidateUser>): MemberSuggestion {
        val key = rowKey(row.funktion, row.lastName, row.firstName)
        val normLastRow = normalize(row.lastName)
        val normFirstRow = normalize(row.firstName)

        val linked = teamUsers.firstOrNull { user ->
            val identity = user.linkedNdsIdentity ?: return@firstOrNull false
            normalize(identity.first) == normLastRow &&
                normalize(identity.second) == normFirstRow &&
                identity.third == row.birthDate
        }
        if (linked != null) {
            return MemberSuggestion(
                rowKey = key,
                candidates = emptyList(),
                preselectedUserId = null,
                alreadyLinkedUserId = linked.userId
            )
        }

        val candidates = teamUsers.mapNotNull { user -> matchCandidate(row, normLastRow, normFirstRow, user) }
        val highCount = candidates.count { it.score == HIGH }
        val preselectedUserId = if (highCount == 1) candidates.first { it.score == HIGH }.userId else null
        val sorted = candidates
            .sortedWith(compareByDescending<MemberSuggestion.Candidate> { it.score == HIGH }.thenByDescending { it.birthdateMatch })
            .take(MAX_CANDIDATES)

        return MemberSuggestion(
            rowKey = key,
            candidates = sorted,
            preselectedUserId = preselectedUserId,
            alreadyLinkedUserId = null
        )
    }

    private fun matchCandidate(
        row: NdsMemberInput,
        normLastRow: String,
        normFirstRow: String,
        user: MatchCandidateUser
    ): MemberSuggestion.Candidate? {
        val normLastCand = normalize(user.lastName)
        val normFirstCand = normalize(user.firstName)

        var score: String? = null
        if (normLastRow + normFirstRow == normLastCand + normFirstCand) {
            score = HIGH
        } else {
            val lastExact = normLastRow == normLastCand
            val firstExact = normFirstRow == normFirstCand
            if (lastExact && !firstExact) {
                if (levenshtein(normFirstRow, normFirstCand) <= 2) score = MEDIUM
            } else if (firstExact && !lastExact) {
                if (levenshtein(normLastRow, normLastCand) <= 2) score = MEDIUM
            }
        }

        if (score == null) return null

        val birthdateMatch = row.birthDate != null && user.birthDate != null && row.birthDate == user.birthDate
        if (birthdateMatch && score == MEDIUM) score = HIGH

        return MemberSuggestion.Candidate(
            userId = user.userId,
            displayName = "${user.firstName} ${user.lastName}",
            score = score,
            birthdateMatch = birthdateMatch
        )
    }

    private fun normalize(s: String): String {
        var result = s.trim().lowercase()
        result = result
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("é", "e").replace("è", "e").replace("ê", "e")
            .replace("à", "a").replace("â", "a")
            .replace("ß", "ss")
        return result.replace(Regex("\\s+"), " ")
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
