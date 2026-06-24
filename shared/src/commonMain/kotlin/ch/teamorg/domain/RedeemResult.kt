package ch.teamorg.domain

sealed interface RedeemResult {
    data object Success : RedeemResult
    data class EmailMismatch(val invitedEmail: String?) : RedeemResult
    data object Expired : RedeemResult
    data object Inactive : RedeemResult
    data object NotFound : RedeemResult
    data class Error(val message: String) : RedeemResult
}
