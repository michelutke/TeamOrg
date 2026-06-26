package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object UsersTable : Table("users") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val displayName = text("display_name")
    val avatarUrl = text("avatar_url").nullable()
    val isSuperAdmin = bool("is_super_admin").default(false)
    val provisional = bool("provisional").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
