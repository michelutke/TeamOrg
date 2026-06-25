package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object NotificationsTable : Table("notifications") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val type = text("type")
    val title = text("title")
    val body = text("body")
    val entityId = uuid("entity_id").nullable()
    val entityType = text("entity_type").nullable()
    val isRead = bool("is_read").default(false)
    val idempotencyKey = text("idempotency_key")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object NotificationSettingsTable : Table("notification_settings") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val eventsNew = bool("events_new").default(true)
    val eventsEdit = bool("events_edit").default(true)
    val eventsCancel = bool("events_cancel").default(true)
    val remindersEnabled = bool("reminders_enabled").default(true)
    val reminderLeadMinutes = integer("reminder_lead_minutes").default(120)
    val coachResponseMode = text("coach_response_mode").default("per_response")
    val absencesEnabled = bool("absences_enabled").default(true)
    val svGames = bool("sv_games").default(true)
    override val primaryKey = PrimaryKey(userId, teamId)
}

object EventReminderOverridesTable : Table("event_reminder_overrides") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val eventId = uuid("event_id").references(EventsTable.id, onDelete = ReferenceOption.CASCADE)
    val reminderLeadMinutes = integer("reminder_lead_minutes").nullable()
    override val primaryKey = PrimaryKey(userId, eventId)
}

object NotificationRemindersTable : Table("notification_reminders") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val eventId = uuid("event_id").references(EventsTable.id, onDelete = ReferenceOption.CASCADE)
    val fireAt = timestamp("fire_at")
    val sent = bool("sent").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}
