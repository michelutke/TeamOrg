package ch.teamorg.data.repository

import ch.teamorg.data.AttendanceCacheManager
import ch.teamorg.data.MutationQueueManager
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.Event
import ch.teamorg.domain.FinalizeBlockedBody
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.SetMemberResponseRequest
import ch.teamorg.domain.SubmitResponseRequest
import ch.teamorg.repository.AttendanceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.errors.IOException
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AttendanceRepositoryImpl(
    private val httpClient: HttpClient,
    private val cacheManager: AttendanceCacheManager,
    private val mutationQueue: MutationQueueManager
) : AttendanceRepository {

    override suspend fun getEventAttendance(eventId: String): Result<List<AttendanceResponse>> {
        return try {
            val responses: List<AttendanceResponse> = httpClient.get("/events/$eventId/attendance").body()
            cacheManager.saveResponses(eventId, responses)
            Result.success(responses)
        } catch (e: ConnectTimeoutException) {
            Result.success(cacheManager.getCachedResponses(eventId))
        } catch (e: HttpRequestTimeoutException) {
            Result.success(cacheManager.getCachedResponses(eventId))
        } catch (e: IOException) {
            Result.success(cacheManager.getCachedResponses(eventId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyResponse(eventId: String): Result<AttendanceResponse?> {
        return try {
            val response: AttendanceResponse? = httpClient.get("/events/$eventId/attendance/me").body()
            response?.let {
                cacheManager.saveResponses(eventId, listOf(it))
            }
            Result.success(response)
        } catch (e: ConnectTimeoutException) {
            val userId = "" // userId not available here — return null for cache miss
            Result.success(null)
        } catch (e: HttpRequestTimeoutException) {
            Result.success(null)
        } catch (e: IOException) {
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitResponse(
        eventId: String,
        request: SubmitResponseRequest
    ): Result<AttendanceResponse> {
        return try {
            val response: AttendanceResponse = httpClient.put("/events/$eventId/attendance/me") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            cacheManager.saveResponses(eventId, listOf(response))
            Result.success(response)
        } catch (e: IOException) {
            // Offline: enqueue and return optimistic response
            val bodyJson = Json.encodeToString(request)
            mutationQueue.enqueue(
                type = "submit_response",
                endpoint = "/events/$eventId/attendance/me",
                method = "PUT",
                body = bodyJson
            )
            val optimistic = AttendanceResponse(
                eventId = eventId,
                userId = "",
                status = request.status,
                reason = request.reason,
                updatedAt = Clock.System.now()
            )
            cacheManager.saveResponses(eventId, listOf(optimistic))
            Result.success(optimistic)
        } catch (e: ConnectTimeoutException) {
            val bodyJson = Json.encodeToString(request)
            mutationQueue.enqueue(
                type = "submit_response",
                endpoint = "/events/$eventId/attendance/me",
                method = "PUT",
                body = bodyJson
            )
            val optimistic = AttendanceResponse(
                eventId = eventId,
                userId = "",
                status = request.status,
                reason = request.reason,
                updatedAt = Clock.System.now()
            )
            cacheManager.saveResponses(eventId, listOf(optimistic))
            Result.success(optimistic)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setMemberResponse(
        eventId: String,
        userId: String,
        status: String,
        unexcused: Boolean
    ): Result<AttendanceResponse> = runCatching {
        httpClient.put("/events/$eventId/attendance/$userId") {
            contentType(ContentType.Application.Json)
            setBody(SetMemberResponseRequest(status = status, unexcused = unexcused))
        }.body()
    }

    override suspend fun finalize(eventId: String): FinalizeResult {
        return try {
            httpClient.post("/events/$eventId/attendance/finalize")
            FinalizeResult.Success
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Conflict) {
                try {
                    val blocked: FinalizeBlockedBody = e.response.body()
                    FinalizeResult.Blocked(reason = blocked.reason, userIds = blocked.userIds)
                } catch (parseEx: Exception) {
                    FinalizeResult.Failure(parseEx)
                }
            } else {
                FinalizeResult.Failure(e)
            }
        } catch (e: Exception) {
            FinalizeResult.Failure(e)
        }
    }

    override suspend fun reopen(eventId: String): Result<Unit> = runCatching {
        httpClient.post("/events/$eventId/attendance/reopen")
        Unit
    }

    override suspend fun awaitingCheckIn(): Result<List<Event>> {
        return try {
            Result.success(httpClient.get("/users/me/events/awaiting-checkin").body())
        } catch (e: ConnectTimeoutException) {
            Result.success(emptyList())
        } catch (e: HttpRequestTimeoutException) {
            Result.success(emptyList())
        } catch (e: IOException) {
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRawAttendance(
        userId: String,
        from: String?,
        to: String?
    ): Result<List<AttendanceResponse>> {
        return try {
            val responses: List<AttendanceResponse> = httpClient.get("/users/$userId/attendance") {
                from?.let { parameter("from", it) }
                to?.let { parameter("to", it) }
            }.body()
            Result.success(responses)
        } catch (e: ConnectTimeoutException) {
            Result.success(emptyList())
        } catch (e: HttpRequestTimeoutException) {
            Result.success(emptyList())
        } catch (e: IOException) {
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTeamAttendance(
        teamId: String,
        from: String?,
        to: String?
    ): Result<List<AttendanceResponse>> {
        return try {
            val responses: List<AttendanceResponse> = httpClient.get("/teams/$teamId/attendance") {
                from?.let { parameter("from", it) }
                to?.let { parameter("to", it) }
            }.body()
            Result.success(responses)
        } catch (e: ConnectTimeoutException) {
            Result.success(emptyList())
        } catch (e: HttpRequestTimeoutException) {
            Result.success(emptyList())
        } catch (e: IOException) {
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
