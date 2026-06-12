package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object ClubsTable : Table("clubs") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val name = text("name")
    val sportType = text("sport_type").default("volleyball")
    val location = text("location").nullable()
    val logoPath = text("logo_path").nullable()
    val status = text("status").default("active")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object ClubRolesTable : Table("club_roles") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val role = text("role") // club_manager
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_club_roles_user_club_role", userId, clubId, role)
    }
}
