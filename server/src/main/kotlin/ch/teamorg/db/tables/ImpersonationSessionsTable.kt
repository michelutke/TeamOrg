package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ImpersonationSessionsTable : Table("impersonation_sessions") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val actorId = uuid("actor_id").references(UsersTable.id)
    val targetId = uuid("target_id").references(UsersTable.id)
    val startedAt = timestamp("started_at").defaultExpression(CurrentTimestamp)
    val expiresAt = timestamp("expires_at")
    val endedAt = timestamp("ended_at").nullable()
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}
