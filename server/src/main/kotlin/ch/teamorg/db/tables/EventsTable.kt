package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

enum class EventType { training, match, other }
enum class EventStatus { active, cancelled }
enum class PatternType { daily, weekly, custom }

object EventSeriesTable : Table("event_series") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val patternType = enumerationByName<PatternType>("pattern_type", 16)
    val weekdays = array<Short>("weekdays").nullable()
    val intervalDays = integer("interval_days").nullable()
    val seriesStartDate = date("series_start_date")
    val seriesEndDate = date("series_end_date").nullable()
    val templateStartTime = time("template_start_time")
    val templateEndTime = time("template_end_time")
    val templateMeetupTime = time("template_meetup_time").nullable()
    val templateTitle = text("template_title")
    val templateType = enumerationByName<EventType>("template_type", 16)
    val templateLocation = text("template_location").nullable()
    val templateDescription = text("template_description").nullable()
    val templateMinAttendees = integer("template_min_attendees").nullable()
    val templateDefaultResponse = text("template_default_response").default("none")
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object EventsTable : Table("events") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val title = text("title")
    val type = enumerationByName<EventType>("type", 16)
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at")
    val meetupAt = timestamp("meetup_at").nullable()
    val location = text("location").nullable()
    val description = text("description").nullable()
    val minAttendees = integer("min_attendees").nullable()
    val status = enumerationByName<EventStatus>("status", 16).default(EventStatus.active)
    val cancelledAt = timestamp("cancelled_at").nullable()
    val seriesId = uuid("series_id").references(EventSeriesTable.id).nullable()
    val seriesSequence = integer("series_sequence").nullable()
    val seriesOverride = bool("series_override").default(false)
    val responseDeadline = timestamp("response_deadline").nullable()
    val checkInEnabled = bool("check_in_enabled").default(false)
    val externalSource = text("external_source").nullable()
    val externalGameId = long("external_game_id").nullable()
    val externalHash = text("external_hash").nullable()
    val externalSyncedAt = timestamp("external_synced_at").nullable()
    val externalStatus = text("external_status").nullable()
    val needsReview = bool("needs_review").default(false)
    val ndsSymbol = text("nds_symbol").nullable()
    val checkInCompletedAt = timestamp("check_in_completed_at").nullable()
    val defaultResponse = text("default_response").default("none")
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object EventTeamsTable : Table("event_teams") {
    val eventId = uuid("event_id").references(EventsTable.id, onDelete = ReferenceOption.CASCADE)
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(eventId, teamId)
}

object EventSubgroupsTable : Table("event_subgroups") {
    val eventId = uuid("event_id").references(EventsTable.id, onDelete = ReferenceOption.CASCADE)
    val subgroupId = uuid("subgroup_id").references(SubGroupsTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(eventId, subgroupId)
}
