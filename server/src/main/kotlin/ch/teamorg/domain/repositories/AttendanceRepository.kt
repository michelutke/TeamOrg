package ch.teamorg.domain.repositories

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant as KInstant

data class AttendanceResponseRow(
    val eventId: UUID,
    val userId: UUID,
    val status: String,
    val reason: String?,
    val abwesenheitRuleId: UUID?,
    val manualOverride: Boolean,
    val unexcused: Boolean,
    val respondedAt: Instant?,
    val updatedAt: Instant
)

data class CheckInRow(
    val eventId: UUID,
    val userId: UUID,
    val status: String,
    val note: String?,
    val setBy: UUID,
    val setAt: Instant,
    val previousStatus: String?,
    val previousSetBy: UUID?
)

data class RawAttendanceRow(
    val eventId: UUID,
    val userId: UUID,
    val responseStatus: String?,
    val recordStatus: String?,
    val eventStartAt: Instant
)

@Serializable
data class AttendanceResponseDto(
    val eventId: String,
    val userId: String,
    val status: String,
    val reason: String? = null,
    val abwesenheitRuleId: String? = null,
    val manualOverride: Boolean = false,
    val unexcused: Boolean = false,
    val respondedAt: KInstant? = null,
    val updatedAt: KInstant
)

@Serializable
data class AttendanceRecordDto(
    val eventId: String,
    val userId: String,
    val status: String,
    val note: String? = null,
    val setBy: String,
    val setAt: KInstant,
    val previousStatus: String? = null,
    val previousSetBy: String? = null
)

@Serializable
data class CheckInEntryResponse(
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val response: AttendanceResponseDto? = null,
    val record: AttendanceRecordDto? = null
)

interface AttendanceRepository {
    suspend fun getEventAttendance(eventId: UUID): List<AttendanceResponseRow>
    suspend fun getMyResponse(eventId: UUID, userId: UUID): AttendanceResponseRow?
    suspend fun upsertResponse(eventId: UUID, userId: UUID, status: String, reason: String?): AttendanceResponseRow
    /**
     * Returns true when now >= (response_deadline ?? start_at). Replaces the old isDeadlinePassed
     * which only checked response_deadline and treated a missing deadline as "not passed".
     */
    suspend fun isPastCutoff(eventId: UUID): Boolean
    /**
     * Coach-initiated upsert. Sets manual_override = true. unexcused is forced false for any
     * non-declined status; only meaningful when status == "declined".
     */
    suspend fun setResponseByCoach(
        eventId: UUID,
        targetUserId: UUID,
        status: String,
        unexcused: Boolean,
        setBy: UUID
    ): AttendanceResponseRow
    suspend fun getCheckIn(eventId: UUID): List<CheckInRow>
    suspend fun getCheckInEntries(eventId: UUID): List<CheckInEntryResponse>
    suspend fun upsertCheckIn(eventId: UUID, userId: UUID, status: String, note: String?, setBy: UUID): CheckInRow
    /**
     * Raw attendance rows for [userId]. When [restrictToTeamIds] is non-null, only rows for events
     * targeting one of those teams are returned (used to scope a coach to the teams they share with
     * the target). A null value returns rows across all of the user's events (self-request).
     */
    suspend fun getRawAttendance(
        userId: UUID,
        from: Instant?,
        to: Instant?,
        restrictToTeamIds: Set<UUID>? = null
    ): List<RawAttendanceRow>
    suspend fun getTeamAttendance(teamId: UUID, from: Instant?, to: Instant?): List<RawAttendanceRow>
    suspend fun bulkInsertAutoDeclines(ruleId: UUID, userId: UUID, eventUserPairs: List<Pair<UUID, UUID>>)
    /**
     * Resets player RSVPs for [eventId] back to 'no-response'. Only rows in
     * 'confirmed'/'declined'/'unsure' are touched; 'declined-auto' (abwesenheit-rule) rows are left
     * intact so auto-decline state is not clobbered. Returns the number of rows reset.
     */
    suspend fun resetResponsesForEvent(eventId: UUID): Int
    /**
     * Returns a map from event id to the count of [ch.teamorg.db.tables.RecordStatus.present]
     * records for each event in [eventIds]. Events with no present rows are absent from the map.
     */
    suspend fun presentCounts(eventIds: List<UUID>): Map<UUID, Int>
}
