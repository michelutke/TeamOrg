package ch.teamorg.domain.repositories

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant as KInstant

sealed class FinalizeResult {
    object Ok : FinalizeResult()
    data class BlockedUnsure(val userIds: List<UUID>) : FinalizeResult()
    data class BlockedNoResponse(val userIds: List<UUID>) : FinalizeResult()
}

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

data class RawAttendanceRow(
    val eventId: UUID,
    val userId: UUID,
    val responseStatus: String?,
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
    /**
     * Finalizes an event's attendance.
     * - Returns [FinalizeResult.BlockedUnsure] if any roster member has status `unsure`.
     * - Resolves `no-response` members per the event's `default_response`:
     *   `accepted`→`confirmed`, `declined`→`declined` (excused), `none`→collect.
     * - Returns [FinalizeResult.BlockedNoResponse] if any unresolved `no-response` remain.
     * - On success sets `events.check_in_completed_at = now` and returns [FinalizeResult.Ok].
     */
    suspend fun finalize(eventId: UUID, byUser: UUID): FinalizeResult

    /** Clears `check_in_completed_at` (sets to null). */
    suspend fun reopen(eventId: UUID)

    suspend fun bulkInsertAutoDeclines(ruleId: UUID, userId: UUID, eventUserPairs: List<Pair<UUID, UUID>>)
    /**
     * Resets player RSVPs for [eventId] back to 'no-response'. Only rows in
     * 'confirmed'/'declined'/'unsure' are touched; 'declined-auto' (abwesenheit-rule) rows are left
     * intact so auto-decline state is not clobbered. Returns the number of rows reset.
     */
    suspend fun resetResponsesForEvent(eventId: UUID): Int
    /**
     * Returns a map from event id to the count of `confirmed` responses for each event in
     * [eventIds]. Events with no confirmed responses are absent from the map.
     */
    suspend fun confirmedCounts(eventIds: List<UUID>): Map<UUID, Int>
}
