package ch.teamorg.repository

import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.CheckInEntry
import ch.teamorg.domain.Event
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.SubmitCheckInRequest
import ch.teamorg.domain.SubmitResponseRequest

interface AttendanceRepository {
    // Player response
    suspend fun getEventAttendance(eventId: String): Result<List<AttendanceResponse>>
    suspend fun getMyResponse(eventId: String): Result<AttendanceResponse?>
    suspend fun submitResponse(eventId: String, request: SubmitResponseRequest): Result<AttendanceResponse>

    // Coach check-in
    suspend fun getCheckIn(eventId: String): Result<List<CheckInEntry>>
    suspend fun submitCheckIn(eventId: String, userId: String, request: SubmitCheckInRequest): Result<Unit>

    // Coach attendance management
    suspend fun setMemberResponse(eventId: String, userId: String, status: String, unexcused: Boolean): Result<AttendanceResponse>
    suspend fun finalize(eventId: String): FinalizeResult
    suspend fun reopen(eventId: String): Result<Unit>
    suspend fun awaitingCheckIn(): Result<List<Event>>

    // Raw data for stats (ADR-007)
    suspend fun getRawAttendance(userId: String, from: String? = null, to: String? = null): Result<List<AttendanceResponse>>
    suspend fun getTeamAttendance(teamId: String, from: String? = null, to: String? = null): Result<List<AttendanceResponse>>
}
