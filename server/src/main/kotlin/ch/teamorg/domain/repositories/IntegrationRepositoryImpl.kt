package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.ClubIntegrationsTable
import ch.teamorg.db.tables.SvSyncStateTable
import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.db.tables.TeamsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class IntegrationRepositoryImpl : IntegrationRepository {
    override suspend fun upsertKey(clubId: UUID, apiKey: String, createdBy: UUID): ClubIntegration = transaction {
        val exists = !ClubIntegrationsTable.selectAll().where { ClubIntegrationsTable.clubId eq clubId }.empty()
        if (exists) {
            ClubIntegrationsTable.update({ ClubIntegrationsTable.clubId eq clubId }) {
                it[ClubIntegrationsTable.apiKey] = apiKey
                it[ClubIntegrationsTable.createdBy] = createdBy
                it[ClubIntegrationsTable.updatedAt] = java.time.Instant.now()
            }
        } else {
            ClubIntegrationsTable.insert {
                it[ClubIntegrationsTable.clubId] = clubId
                it[ClubIntegrationsTable.apiKey] = apiKey
                it[ClubIntegrationsTable.createdBy] = createdBy
            }
        }

        ClubIntegrationsTable.selectAll().where { ClubIntegrationsTable.clubId eq clubId }
            .map(::rowToIntegration)
            .single()
    }

    override suspend fun getIntegration(clubId: UUID): ClubIntegration? = transaction {
        ClubIntegrationsTable.selectAll().where { ClubIntegrationsTable.clubId eq clubId }
            .map(::rowToIntegration)
            .singleOrNull()
    }

    override suspend fun getApiKey(clubId: UUID): String? = transaction {
        ClubIntegrationsTable.select(ClubIntegrationsTable.apiKey)
            .where { ClubIntegrationsTable.clubId eq clubId }
            .map { it[ClubIntegrationsTable.apiKey] }
            .singleOrNull()
    }

    override suspend fun setKeyValidity(clubId: UUID, valid: Boolean, validatedAt: java.time.Instant, pausedReason: String?): ClubIntegration = transaction {
        ClubIntegrationsTable.update({ ClubIntegrationsTable.clubId eq clubId }) {
            it[ClubIntegrationsTable.keyValid] = valid
            it[ClubIntegrationsTable.lastValidatedAt] = validatedAt
            it[ClubIntegrationsTable.syncPausedReason] = pausedReason
            it[ClubIntegrationsTable.updatedAt] = java.time.Instant.now()
        }

        ClubIntegrationsTable.selectAll().where { ClubIntegrationsTable.clubId eq clubId }
            .map(::rowToIntegration)
            .single()
    }

    override suspend fun deleteIntegration(clubId: UUID): Unit = transaction {
        ClubIntegrationsTable.deleteWhere { Op.build { ClubIntegrationsTable.clubId eq clubId } }
    }

    override suspend fun listLinksForClub(clubId: UUID): List<TeamSvLink> = transaction {
        (TeamSvLinksTable innerJoin TeamsTable).selectAll().where { TeamsTable.clubId eq clubId }
            .map(::rowToLink)
    }

    override suspend fun listLinksForTeam(teamId: UUID): List<TeamSvLink> = transaction {
        TeamSvLinksTable.selectAll().where { TeamSvLinksTable.teamId eq teamId }
            .map(::rowToLink)
    }

    override suspend fun deprecateLinksForClub(clubId: UUID): Unit = transaction {
        val teamIds = TeamsTable.select(TeamsTable.id).where { TeamsTable.clubId eq clubId }
            .map { it[TeamsTable.id] }
        if (teamIds.isNotEmpty()) {
            TeamSvLinksTable.update({
                (TeamSvLinksTable.teamId inList teamIds) and TeamSvLinksTable.deprecatedAt.isNull()
            }) {
                it[TeamSvLinksTable.deprecatedAt] = java.time.Instant.now()
            }
        }
    }

    override suspend fun getState(clubId: UUID): SvSyncState? = transaction {
        SvSyncStateTable.selectAll().where { SvSyncStateTable.clubId eq clubId }
            .map(::rowToSyncState)
            .singleOrNull()
    }

    override suspend fun upsertState(clubId: UUID, lastSyncedAt: java.time.Instant?, lastStatus: String?, lastError: String?): SvSyncState = transaction {
        val exists = !SvSyncStateTable.selectAll().where { SvSyncStateTable.clubId eq clubId }.empty()
        if (exists) {
            SvSyncStateTable.update({ SvSyncStateTable.clubId eq clubId }) {
                it[SvSyncStateTable.lastSyncedAt] = lastSyncedAt
                it[SvSyncStateTable.lastStatus] = lastStatus
                it[SvSyncStateTable.lastError] = lastError
            }
        } else {
            SvSyncStateTable.insert {
                it[SvSyncStateTable.clubId] = clubId
                it[SvSyncStateTable.lastSyncedAt] = lastSyncedAt
                it[SvSyncStateTable.lastStatus] = lastStatus
                it[SvSyncStateTable.lastError] = lastError
            }
        }

        SvSyncStateTable.selectAll().where { SvSyncStateTable.clubId eq clubId }
            .map(::rowToSyncState)
            .single()
    }

    private fun rowToIntegration(row: ResultRow) = ClubIntegration(
        clubId = row[ClubIntegrationsTable.clubId].toString(),
        provider = row[ClubIntegrationsTable.provider],
        keyValid = row[ClubIntegrationsTable.keyValid],
        lastValidatedAt = row[ClubIntegrationsTable.lastValidatedAt]?.toString(),
        syncPausedReason = row[ClubIntegrationsTable.syncPausedReason],
        createdBy = row[ClubIntegrationsTable.createdBy]?.toString(),
        createdAt = row[ClubIntegrationsTable.createdAt].toString(),
        updatedAt = row[ClubIntegrationsTable.updatedAt].toString()
    )

    private fun rowToLink(row: ResultRow) = TeamSvLink(
        id = row[TeamSvLinksTable.id].toString(),
        teamId = row[TeamSvLinksTable.teamId].toString(),
        svTeamId = row[TeamSvLinksTable.svTeamId],
        svSeasonalTeamId = row[TeamSvLinksTable.svSeasonalTeamId],
        svLeagueCaption = row[TeamSvLinksTable.svLeagueCaption],
        svGender = row[TeamSvLinksTable.svGender],
        deprecatedAt = row[TeamSvLinksTable.deprecatedAt]?.toString(),
        createdAt = row[TeamSvLinksTable.createdAt].toString()
    )

    private fun rowToSyncState(row: ResultRow) = SvSyncState(
        clubId = row[SvSyncStateTable.clubId].toString(),
        lastSyncedAt = row[SvSyncStateTable.lastSyncedAt]?.toString(),
        lastStatus = row[SvSyncStateTable.lastStatus],
        lastError = row[SvSyncStateTable.lastError]
    )
}
