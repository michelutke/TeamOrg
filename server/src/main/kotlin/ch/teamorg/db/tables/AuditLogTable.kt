package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object AuditLogTable : Table("audit_log") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val actorId = uuid("actor_id").references(UsersTable.id)
    val actorEmail = text("actor_email")
    val action = text("action")
    val targetType = text("target_type").nullable()
    val targetId = text("target_id").nullable()
    val details = text("details").nullable()              // stored as JSON string
    val impersonationContext = text("impersonation_context").nullable() // JSON string
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}
