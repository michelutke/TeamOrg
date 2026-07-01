package ch.teamorg.fake

import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.CheckInEntry
import ch.teamorg.domain.Event
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.SubmitCheckInRequest
import ch.teamorg.domain.SubmitResponseRequest
import ch.teamorg.repository.AttendanceRepository
import kotlinx.datetime.Clock

class FakeAttendanceRepository : AttendanceRepository {

    var getEventAttendanceResult: Result<List<AttendanceResponse>> = Result.success(emptyList())
    var getMyResponseResult: Result<AttendanceResponse?> = Result.success(null)
    var submitResponseResult: Result<AttendanceResponse> = Result.success(
        AttendanceResponse(eventId = "e1", userId = "u1", status = "confirmed", updatedAt = Clock.System.now())
    )
    var getCheckInResult: Result<List<CheckInEntry>> = Result.success(emptyList())
    var submitCheckInResult: Result<Unit> = Result.success(Unit)
    var setMemberResponseResult: Result<AttendanceResponse> = Result.success(
        AttendanceResponse(eventId = "e1", userId = "u1", status = "confirmed", updatedAt = Clock.System.now())
    )
    var finalizeResult: FinalizeResult = FinalizeResult.Success
    var reopenResult: Result<Unit> = Result.success(Unit)
    var awaitingCheckInResult: Result<List<Event>> = Result.success(emptyList())
    var getRawAttendanceResult: Result<List<AttendanceResponse>> = Result.success(emptyList())
    var getTeamAttendanceResult: Result<List<AttendanceResponse>> = Result.success(emptyList())

    var lastSetMemberEventId: String? = null
    var lastSetMemberUserId: String? = null
    var lastSetMemberStatus: String? = null
    var lastSetMemberUnexcused: Boolean? = null
    var lastFinalizeEventId: String? = null
    var lastReopenEventId: String? = null

    fun reset() {
        getEventAttendanceResult = Result.success(emptyList())
        getMyResponseResult = Result.success(null)
        submitResponseResult = Result.success(
            AttendanceResponse(eventId = "e1", userId = "u1", status = "confirmed", updatedAt = Clock.System.now())
        )
        getCheckInResult = Result.success(emptyList())
        submitCheckInResult = Result.success(Unit)
        setMemberResponseResult = Result.success(
            AttendanceResponse(eventId = "e1", userId = "u1", status = "confirmed", updatedAt = Clock.System.now())
        )
        finalizeResult = FinalizeResult.Success
        reopenResult = Result.success(Unit)
        awaitingCheckInResult = Result.success(emptyList())
        getRawAttendanceResult = Result.success(emptyList())
        getTeamAttendanceResult = Result.success(emptyList())
        lastSetMemberEventId = null
        lastSetMemberUserId = null
        lastSetMemberStatus = null
        lastSetMemberUnexcused = null
        lastFinalizeEventId = null
        lastReopenEventId = null
    }

    override suspend fun getEventAttendance(eventId: String): Result<List<AttendanceResponse>> = getEventAttendanceResult

    override suspend fun getMyResponse(eventId: String): Result<AttendanceResponse?> = getMyResponseResult

    override suspend fun submitResponse(eventId: String, request: SubmitResponseRequest): Result<AttendanceResponse> = submitResponseResult

    override suspend fun getCheckIn(eventId: String): Result<List<CheckInEntry>> = getCheckInResult

    override suspend fun submitCheckIn(eventId: String, userId: String, request: SubmitCheckInRequest): Result<Unit> = submitCheckInResult

    override suspend fun setMemberResponse(
        eventId: String,
        userId: String,
        status: String,
        unexcused: Boolean
    ): Result<AttendanceResponse> {
        lastSetMemberEventId = eventId
        lastSetMemberUserId = userId
        lastSetMemberStatus = status
        lastSetMemberUnexcused = unexcused
        return setMemberResponseResult
    }

    override suspend fun finalize(eventId: String): FinalizeResult {
        lastFinalizeEventId = eventId
        return finalizeResult
    }

    override suspend fun reopen(eventId: String): Result<Unit> {
        lastReopenEventId = eventId
        return reopenResult
    }

    override suspend fun awaitingCheckIn(): Result<List<Event>> = awaitingCheckInResult

    override suspend fun getRawAttendance(userId: String, from: String?, to: String?): Result<List<AttendanceResponse>> = getRawAttendanceResult

    override suspend fun getTeamAttendance(teamId: String, from: String?, to: String?): Result<List<AttendanceResponse>> = getTeamAttendanceResult
}
