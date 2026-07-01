package ch.teamorg.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String,
    val title: String,
    val type: String,           // "training" | "match" | "other"
    val startAt: Instant,
    val endAt: Instant,
    val meetupAt: Instant? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val status: String,         // "active" | "cancelled"
    val cancelledAt: Instant? = null,
    val seriesId: String? = null,
    val seriesSequence: Int? = null,
    val seriesOverride: Boolean = false,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val teamIds: List<String> = emptyList(),
    val subgroupIds: List<String> = emptyList(),
    val externalSource: String? = null,
    val presentCount: Int = 0,
    val checkInStatus: String = "open",     // "open"|"locked"|"awaiting_checkin"|"done"
    val checkInCompletedAt: Instant? = null,
    val defaultResponse: String = "none",   // "none"|"accepted"|"declined"
)

@Serializable
data class EventWithTeams(
    val event: Event,
    val matchedTeams: List<MatchedTeam> = emptyList()
)

@Serializable
data class MatchedTeam(
    val id: String,
    val name: String
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val type: String,
    val startAt: Instant,
    val endAt: Instant,
    val meetupAt: Instant? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val teamIds: List<String>,
    val subgroupIds: List<String> = emptyList(),
    val recurring: RecurringPattern? = null
)

@Serializable
data class RecurringPattern(
    val patternType: String,    // "daily" | "weekly" | "custom"
    val weekdays: List<Int>? = null,  // 0=Mon..6=Sun
    val intervalDays: Int? = null,
    val seriesEndDate: String? = null // ISO date string "2026-08-01"
)

@Serializable
data class EditEventRequest(
    val title: String? = null,
    val type: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val meetupAt: Instant? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val teamIds: List<String>? = null,
    val subgroupIds: List<String>? = null,
    val scope: String? = null  // "this_only" | "this_and_future" | "all"
)

@Serializable
data class SubGroup(
    val id: String,
    val teamId: String,
    val name: String,
    val memberCount: Int = 0
)

enum class EventType(val value: String) {
    TRAINING("training"),
    MATCH("match"),
    OTHER("other");
    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}
