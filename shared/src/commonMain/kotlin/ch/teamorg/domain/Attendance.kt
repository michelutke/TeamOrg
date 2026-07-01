package ch.teamorg.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AttendanceResponse(
    val eventId: String,
    val userId: String,
    val status: String,           // "confirmed"|"declined"|"unsure"|"declined-auto"|"no-response"
    val reason: String? = null,
    val abwesenheitRuleId: String? = null,
    val manualOverride: Boolean = false,
    val respondedAt: Instant? = null,
    val updatedAt: Instant,
    val unexcused: Boolean = false
)

@Serializable
data class AttendanceRecord(
    val eventId: String,
    val userId: String,
    val status: String,           // "present"|"absent"|"excused"
    val note: String? = null,
    val setBy: String,
    val setAt: Instant,
    val previousStatus: String? = null,
    val previousSetBy: String? = null
)

@Serializable
data class CheckInEntry(
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val response: AttendanceResponse? = null,
    val record: AttendanceRecord? = null
)

@Serializable
data class SubmitResponseRequest(
    val status: String,
    val reason: String? = null
)

@Serializable
data class SubmitCheckInRequest(
    val status: String,
    val note: String? = null
)

@Serializable
data class BackfillStatus(
    val status: String   // "pending"|"done"|"failed"
)

@Serializable
data class SetMemberResponseRequest(
    val status: String,
    val unexcused: Boolean
)

@Serializable
data class FinalizeBlockedBody(
    val reason: String,
    val userIds: List<String>
)

sealed interface FinalizeResult {
    data object Success : FinalizeResult
    data class Blocked(val reason: String, val userIds: List<String>) : FinalizeResult
    data class Failure(val cause: Throwable) : FinalizeResult
}

// Client-side computed stats (ADR-007: no server stats endpoint)
data class AttendanceStats(
    val totalEvents: Int,
    val presentCount: Int,
    val absentCount: Int,
    val excusedCount: Int,
    val presencePct: Float,        // 0.0..1.0
    val trainingPresencePct: Float,
    val matchPresencePct: Float
)
