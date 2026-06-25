package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object SystemUsers {
    val VOLLEY_MANAGER: UUID = UUID.fromString("00000000-0000-4000-a000-0000000000a1")
}

object ClubIntegrationsTable : Table("club_integrations") {
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = text("provider").default("swissvolley")
    val apiKey = text("api_key")
    val keyValid = bool("key_valid").nullable()
    val lastValidatedAt = timestamp("last_validated_at").nullable()
    val syncPausedReason = text("sync_paused_reason").nullable()
    val createdBy = uuid("created_by").references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(clubId)
}

object TeamSvLinksTable : Table("team_sv_links") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val svTeamId = integer("sv_team_id")
    val svSeasonalTeamId = integer("sv_seasonal_team_id").nullable()
    val svLeagueCaption = text("sv_league_caption").nullable()
    val svGender = text("sv_gender").nullable()
    val deprecatedAt = timestamp("deprecated_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_team_sv_links_team_svid", teamId, svTeamId)
    }
}

object SvSyncStateTable : Table("sv_sync_state") {
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val lastSyncedAt = timestamp("last_synced_at").nullable()
    val lastStatus = text("last_status").nullable()
    val lastError = text("last_error").nullable()
    override val primaryKey = PrimaryKey(clubId)
}
