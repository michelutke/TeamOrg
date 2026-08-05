package ch.teamorg.domain

/** Outcome of a self-deletion request. The raw HTTP status never reaches the UI. */
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult
    /** 401 — the confirmation password did not match. */
    data object InvalidPassword : DeleteAccountResult
    /** 409 — the caller still owns these clubs and must hand them over first. */
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountResult
    data class Error(val message: String) : DeleteAccountResult
}
