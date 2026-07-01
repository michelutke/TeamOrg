package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

// Note: ResponseStatus values contain hyphens ('declined-auto', 'no-response') which are invalid
// Kotlin identifiers, so we use plain text columns for attendance_responses.status.

enum class PresetType { holidays, injury, work, school, travel, other }
enum class RuleType { recurring, period }

object AttendanceResponsesTable : Table("attendance_responses") {
    val eventId = uuid("event_id").references(EventsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    // Plain text — status values contain hyphens ('declined-auto', 'no-response')
    val status = text("status").default("no-response")
    val reason = text("reason").nullable()
    val abwesenheitRuleId = uuid("abwesenheit_rule_id")
        .references(AbwesenheitRulesTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val manualOverride = bool("manual_override").default(false)
    val respondedAt = timestamp("responded_at").nullable()
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val unexcused = bool("unexcused").default(false)
    override val primaryKey = PrimaryKey(eventId, userId)
}

object AbwesenheitRulesTable : Table("abwesenheit_rules") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val presetType = enumerationByName<PresetType>("preset_type", 20)
    val label = text("label")
    val bodyPart = text("body_part").nullable()
    val ruleType = enumerationByName<RuleType>("rule_type", 10)
    val weekdays = array<Short>("weekdays").nullable()
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}
