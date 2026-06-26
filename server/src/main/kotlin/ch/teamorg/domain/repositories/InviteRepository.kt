package ch.teamorg.domain.repositories

import ch.teamorg.domain.models.InviteDetails
import ch.teamorg.domain.models.InviteLink
import java.util.*

interface InviteRepository {
    suspend fun create(
        teamId: UUID,
        createdByUserId: UUID,
        role: String,
        email: String? = null,
        reusable: Boolean = false,
        expiresInDays: Int? = null
    ): InviteLink

    suspend fun createClubInvite(
        clubId: UUID,
        createdByUserId: UUID,
        role: String,
        email: String,
        expiresInDays: Int? = null
    ): InviteLink

    /** Personal team invite bound to a roster member; redeeming it claims that member. */
    suspend fun createNdsMemberInvite(
        teamId: UUID,
        createdByUserId: UUID,
        role: String,
        email: String?,
        ndsMemberId: UUID,
        expiresInDays: Int? = null
    ): InviteLink

    suspend fun findByToken(token: String): InviteLink?
    suspend fun getInviteDetails(token: String): InviteDetails?
    suspend fun setActive(token: String, active: Boolean)
    suspend fun isMember(teamId: UUID, userId: UUID, role: String): Boolean

    /** Performs the actual role-row insert per the contract. Returns the outcome. */
    suspend fun redeem(invite: InviteLink, userId: UUID): RedeemResult
}

enum class RedeemResult {
    OK,
    ALREADY_MEMBER
}
