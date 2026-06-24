package ch.teamorg.repository

import ch.teamorg.domain.InviteDetails
import ch.teamorg.domain.RedeemResult

interface InviteRepository {
    suspend fun getInviteDetails(token: String): Result<InviteDetails>
    suspend fun redeemInvite(token: String): RedeemResult
}
