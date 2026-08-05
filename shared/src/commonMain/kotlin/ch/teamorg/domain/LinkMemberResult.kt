package ch.teamorg.domain

/**
 * Outcome of linking an account to an imported NDS roster row.
 *
 * The server returns 409 for two distinct causes — the account already holds a different roster row of
 * this team, or this row is already held by a real (non-provisional) account. Both collapse to
 * [Conflict]: the coach's next action is the same either way (pick another account, or fix it on web).
 */
sealed interface LinkMemberResult {
    data object Success : LinkMemberResult
    data object Conflict : LinkMemberResult
    /** 400 — the account cannot be linked, e.g. it is itself an import placeholder. */
    data object NotLinkable : LinkMemberResult
    data class Error(val message: String) : LinkMemberResult
}
