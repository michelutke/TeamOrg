package ch.teamorg.fake

import ch.teamorg.domain.InviteDetails
import ch.teamorg.domain.RedeemResult
import ch.teamorg.repository.InviteRepository

class FakeInviteRepository : InviteRepository {

    private fun defaultDetails() = InviteDetails(
        token = "abc123",
        scope = "team",
        teamName = "Team A",
        clubName = "Club A",
        role = "player",
        invitedBy = "Coach",
        expiresAt = "2099-01-01T00:00:00Z",
        alreadyRedeemed = false
    )

    var getInviteDetailsResult: Result<InviteDetails> = Result.success(defaultDetails())
    var redeemInviteResult: RedeemResult = RedeemResult.Success

    var lastDetailsToken: String? = null
    var lastRedeemToken: String? = null

    fun reset() {
        getInviteDetailsResult = Result.success(defaultDetails())
        redeemInviteResult = RedeemResult.Success
        lastDetailsToken = null
        lastRedeemToken = null
    }

    override suspend fun getInviteDetails(token: String): Result<InviteDetails> {
        lastDetailsToken = token
        return getInviteDetailsResult
    }

    override suspend fun redeemInvite(token: String): RedeemResult {
        lastRedeemToken = token
        return redeemInviteResult
    }
}
