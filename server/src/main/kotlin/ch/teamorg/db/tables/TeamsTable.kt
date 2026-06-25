package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object TeamsTable : Table("teams") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val name = text("name")
    val description = text("description").nullable()
    val appearanceShape = text("appearance_shape").nullable()
    val appearanceColor = text("appearance_color").nullable()
    val archivedAt = timestamp("archived_at").nullable()
    val gamesSyncEnabled = bool("games_sync_enabled").default(false)
    val predecessorTeamId = uuid("predecessor_team_id").references(id).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object TeamRolesTable : Table("team_roles") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val role = text("role") // coach, player
    val jerseyNumber = integer("jersey_number").nullable()
    val position = text("position").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_team_roles_user_team_role", userId, teamId, role)
    }
}
