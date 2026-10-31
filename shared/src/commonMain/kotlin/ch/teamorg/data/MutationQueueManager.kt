package ch.teamorg.data

import ch.teamorg.db.TeamorgDb
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class MutationQueueManager(
    private val db: TeamorgDb,
    private val httpClient: HttpClient
) {
    private val flushMutex = Mutex()

    fun enqueue(type: String, endpoint: String, method: String, body: String?) {
        db.attendanceQueries.enqueueMutation(
            type = type,
            endpoint = endpoint,
            method = method,
            body = body,
            created_at = Clock.System.now().toEpochMilliseconds()
        )
    }

    suspend fun flushQueue(): List<FlushResult> = flushMutex.withLock {
        val pending = db.attendanceQueries.getPendingMutations().executeAsList()
        val results = mutableListOf<FlushResult>()
        for (mutation in pending) {
            try {
                val response: HttpResponse = httpClient.request(mutation.endpoint) {
                    this.method = HttpMethod.parse(mutation.method)
                    mutation.body?.let {
                        contentType(ContentType.Application.Json)
                        setBody(it)
                    }
                }
                if (response.status.isSuccess()) {
                    db.attendanceQueries.deleteMutation(mutation.id)
                    results.add(FlushResult.Success(mutation.id))
                } else if (response.status == HttpStatusCode.Conflict) {
                    // 409 = deadline passed; remove from queue and report conflict to UI
                    db.attendanceQueries.deleteMutation(mutation.id)
                    results.add(FlushResult.Conflict(mutation.id, "Response deadline has passed"))
                } else {
                    db.attendanceQueries.incrementRetryCount(mutation.id)
                    results.add(FlushResult.Error(mutation.id, "HTTP ${response.status.value}"))
                }
            } catch (e: Exception) {
                db.attendanceQueries.incrementRetryCount(mutation.id)
                results.add(FlushResult.Error(mutation.id, e.message ?: "Unknown error"))
            }
        }
        results
    }

    fun hasPendingMutations(): Boolean =
        db.attendanceQueries.getPendingMutations().executeAsList().isNotEmpty()
}

sealed class FlushResult {
    data class Success(val id: Long) : FlushResult()
    data class Conflict(val id: Long, val message: String) : FlushResult()
    data class Error(val id: Long, val message: String) : FlushResult()
}
