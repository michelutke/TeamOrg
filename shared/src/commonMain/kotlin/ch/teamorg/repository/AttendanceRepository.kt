package ch.teamorg.repository

import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.SubmitResponseRequest

interface AttendanceRepository {
    // Player response
    suspend fun getEventAttendance(eventId: String): Result<List<AttendanceResponse>>
    suspend fun getMyResponse(eventId: String): Result<AttendanceResponse?>
    suspend fun submitResponse(eventId: String, request: SubmitResponseRequest): Result<AttendanceResponse>

    // Coach attendance management
    suspend fun setMemberResponse(eventId: String, userId: String, status: String, unexcused: Boolean): Result<AttendanceResponse>
    suspend fun finalize(eventId: String): FinalizeResult
    suspend fun reopen(eventId: String): Result<Unit>

    // Raw data for stats (ADR-007)
    suspend fun getRawAttendance(userId: String, from: String? = null, to: String? = null): Result<List<AttendanceResponse>>
    suspend fun getTeamAttendance(teamId: String, from: String? = null, to: String? = null): Result<List<AttendanceResponse>>
}
