package ch.teamorg.domain.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
data class Event(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val title: String,
    val type: String,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    @Serializable(with = InstantSerializer::class) val meetupAt: Instant?,
    val location: String?,
    val description: String?,
    val minAttendees: Int?,
    val status: String,
    @Serializable(with = InstantSerializer::class) val cancelledAt: Instant?,
    @Serializable(with = UUIDSerializer::class) val seriesId: UUID?,
    val seriesSequence: Int?,
    val seriesOverride: Boolean,
    @Serializable(with = UUIDSerializer::class) val createdBy: UUID,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    val teamIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val subgroupIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val externalSource: String? = null,
    val externalStatus: String? = null,
    val needsReview: Boolean = false,
    val presentCount: Int = 0
)

@Serializable
data class EventWithTeams(
    val event: Event,
    val matchedTeams: List<MatchedTeam>
)

@Serializable
data class SyncedEventRef(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val externalHash: String?,
    val status: String,
    val externalStatus: String?,
    val finished: Boolean,
    val live: Boolean
)

@Serializable
data class MatchedTeam(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String
)

@Serializable
data class EventSeries(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val patternType: String,
    val weekdays: List<Short>?,
    val intervalDays: Int?,
    @Serializable(with = LocalDateSerializer::class) val seriesStartDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val seriesEndDate: LocalDate?,
    @Serializable(with = LocalTimeSerializer::class) val templateStartTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val templateEndTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val templateMeetupTime: LocalTime?,
    val templateTitle: String,
    val templateType: String,
    val templateLocation: String?,
    val templateDescription: String?,
    val templateMinAttendees: Int?,
    @Serializable(with = UUIDSerializer::class) val createdBy: UUID,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant
)

@Serializable
data class ImportableSeries(
    @Serializable(with = UUIDSerializer::class) val seriesId: UUID,
    val patternType: String,
    val weekdays: List<Short>?,
    val intervalDays: Int?,
    @Serializable(with = LocalTimeSerializer::class) val templateStartTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val templateEndTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val templateMeetupTime: LocalTime?,
    val templateTitle: String,
    val templateType: String,
    val templateLocation: String?,
    val templateMinAttendees: Int?,
    @Serializable(with = LocalDateSerializer::class) val seriesStartDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val seriesEndDate: LocalDate?,
    val label: String
)

@Serializable
data class ImportableSeriesResult(
    val hasOwnSeries: Boolean,
    val predecessorTeamId: String?,
    val series: List<ImportableSeries>
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val type: String,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    @Serializable(with = InstantSerializer::class) val meetupAt: Instant? = null,
    val location: String? = null,
    val description: String? = null,
    val minAttendees: Int? = null,
    val teamIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val subgroupIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val recurring: RecurringPattern? = null,
    @Serializable(with = InstantSerializer::class) val responseDeadline: Instant? = null
)

@Serializable
data class RecurringPattern(
    val patternType: String,
    // Nullable fields need defaults: the client omits them when null (kotlinx drops
    // nulls), so without defaults deserialization fails with MissingFieldException.
    val weekdays: List<Short>? = null,
    val intervalDays: Int? = null,
    @Serializable(with = LocalDateSerializer::class) val seriesEndDate: LocalDate? = null
)

@Serializable
data class EditEventRequest(
    val title: String?,
    val type: String?,
    @Serializable(with = InstantSerializer::class) val startAt: Instant?,
    @Serializable(with = InstantSerializer::class) val endAt: Instant?,
    @Serializable(with = InstantSerializer::class) val meetupAt: Instant?,
    val location: String?,
    val description: String?,
    val minAttendees: Int?,
    val teamIds: List<@Serializable(with = UUIDSerializer::class) UUID>?,
    val subgroupIds: List<@Serializable(with = UUIDSerializer::class) UUID>?
)

enum class RecurringScope { this_only, this_and_future, all }
