package ch.teamorg.domain.repositories

import java.util.UUID

data class ClubIntegration(
    val clubId: String,
    val provider: String,
    val keyValid: Boolean?,
    val lastValidatedAt: String?,
    val syncPausedReason: String?,
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String
)

data class TeamSvLink(
    val id: String,
    val teamId: String,
    val svTeamId: Int,
    val svSeasonalTeamId: Int?,
    val svLeagueCaption: String?,
    val svGender: String?,
    val deprecatedAt: String?,
    val createdAt: String
)

data class SvSyncState(
    val clubId: String,
    val lastSyncedAt: String?,
    val lastStatus: String?,
    val lastError: String?
)

interface IntegrationRepository {
    suspend fun upsertKey(clubId: UUID, apiKey: String, createdBy: UUID): ClubIntegration
    suspend fun getIntegration(clubId: UUID): ClubIntegration?
    suspend fun getApiKey(clubId: UUID): String?
    suspend fun setKeyValidity(clubId: UUID, valid: Boolean, validatedAt: java.time.Instant, pausedReason: String?): ClubIntegration
    suspend fun deleteIntegration(clubId: UUID)

    suspend fun listLinksForClub(clubId: UUID): List<TeamSvLink>
    suspend fun listLinksForTeam(teamId: UUID): List<TeamSvLink>
    suspend fun deprecateLinksForClub(clubId: UUID)

    suspend fun getState(clubId: UUID): SvSyncState?
    suspend fun upsertState(clubId: UUID, lastSyncedAt: java.time.Instant?, lastStatus: String?, lastError: String?): SvSyncState
}
