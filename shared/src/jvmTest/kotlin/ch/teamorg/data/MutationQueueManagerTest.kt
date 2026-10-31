package ch.teamorg.data

import ch.teamorg.db.TeamorgDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutationQueueManagerTest {

    private lateinit var db: TeamorgDb
    private val sent = mutableListOf<HttpRequestData>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("", HttpStatusCode.OK)
    }

    private lateinit var manager: MutationQueueManager

    @BeforeTest
    fun setUp() {
        db = TeamorgDb(DatabaseDriverFactory().createDriver())
        sent.clear()
        handler = { respond("", HttpStatusCode.OK) }
        val engine = MockEngine { request ->
            sent += request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(DefaultRequest) { url("http://localhost") }
        }
        manager = MutationQueueManager(db, client)
    }

    private fun pendingRows() = db.attendanceQueries.getPendingMutations().executeAsList()

    private fun enqueueResponse(eventId: String, body: String? = """{"status":"confirmed"}""") =
        manager.enqueue("submit_response", "/events/$eventId/attendance/me", "PUT", body)

    private fun HttpRequestData.path() = url.encodedPath

    @Test
    fun enqueue_storesMutationAndMarksQueuePending() {
        assertFalse(manager.hasPendingMutations())

        enqueueResponse("e1")

        assertTrue(manager.hasPendingMutations())
        val row = pendingRows().single()
        assertEquals("submit_response", row.type)
        assertEquals("/events/e1/attendance/me", row.endpoint)
        assertEquals("PUT", row.method)
        assertEquals("""{"status":"confirmed"}""", row.body)
        assertEquals(0L, row.retry_count)
    }

    @Test
    fun flushQueue_emptyQueue_sendsNothing() = runTest {
        assertEquals(emptyList(), manager.flushQueue())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun flushQueue_sendsEveryQueuedMutationInEnqueueOrderAndClearsQueue() = runTest {
        enqueueResponse("e1")
        enqueueResponse("e2")
        enqueueResponse("e3")

        val results = manager.flushQueue()

        assertEquals(
            listOf("/events/e1/attendance/me", "/events/e2/attendance/me", "/events/e3/attendance/me"),
            sent.map { it.path() }
        )
        assertTrue(results.all { it is FlushResult.Success })
        assertEquals(3, results.size)
        assertFalse(manager.hasPendingMutations())
    }

    @Test
    fun flushQueue_ordersByCreationTimeNotInsertionOrder() = runTest {
        db.attendanceQueries.enqueueMutation("t", "/third", "PUT", null, 3_000L)
        db.attendanceQueries.enqueueMutation("t", "/first", "PUT", null, 1_000L)
        db.attendanceQueries.enqueueMutation("t", "/second", "PUT", null, 2_000L)

        manager.flushQueue()

        assertEquals(listOf("/first", "/second", "/third"), sent.map { it.path() })
    }

    @Test
    fun flushQueue_replaysMethodAndJsonBody() = runTest {
        manager.enqueue("submit_response", "/events/e1/attendance/me", "PUT", """{"status":"declined","reason":"sick"}""")

        manager.flushQueue()

        val request = sent.single()
        assertEquals("PUT", request.method.value)
        val body = assertIs<TextContent>(request.body)
        assertEquals("""{"status":"declined","reason":"sick"}""", body.text)
        assertEquals("application/json", body.contentType?.withoutParameters()?.toString())
    }

    @Test
    fun flushQueue_mutationWithoutBody_sendsNoBody() = runTest {
        manager.enqueue("delete_rule", "/abwesenheit/r1", "DELETE", null)

        manager.flushQueue()

        assertEquals("DELETE", sent.single().method.value)
        assertEquals(0L, sent.single().body.contentLength ?: 0L)
    }

    @Test
    fun flushQueue_successResultsCarryMutationIds() = runTest {
        enqueueResponse("e1")
        enqueueResponse("e2")
        val ids = pendingRows().map { it.id }

        val results = manager.flushQueue()

        assertEquals(ids, results.map { (it as FlushResult.Success).id })
    }

    @Test
    fun flushQueue_conflict_isReportedAndDroppedWithoutRetry() = runTest {
        handler = { respond("", HttpStatusCode.Conflict) }
        enqueueResponse("e1")
        val id = pendingRows().single().id

        val results = manager.flushQueue()

        val conflict = assertIs<FlushResult.Conflict>(results.single())
        assertEquals(id, conflict.id)
        assertFalse(manager.hasPendingMutations())
        manager.flushQueue()
        assertEquals(1, sent.size)
    }

    @Test
    fun flushQueue_conflictDoesNotBlockLaterMutations() = runTest {
        handler = { request ->
            if (request.path().endsWith("e1/attendance/me")) respond("", HttpStatusCode.Conflict)
            else respond("", HttpStatusCode.OK)
        }
        enqueueResponse("e1")
        enqueueResponse("e2")

        val results = manager.flushQueue()

        assertIs<FlushResult.Conflict>(results[0])
        assertIs<FlushResult.Success>(results[1])
        assertFalse(manager.hasPendingMutations())
    }

    @Test
    fun flushQueue_serverError_keepsMutationAndCountsRetry() = runTest {
        handler = { respond("", HttpStatusCode.InternalServerError) }
        enqueueResponse("e1")

        val results = manager.flushQueue()

        assertEquals("HTTP 500", assertIs<FlushResult.Error>(results.single()).message)
        assertTrue(manager.hasPendingMutations())
        assertEquals(1L, pendingRows().single().retry_count)

        manager.flushQueue()
        assertEquals(2L, pendingRows().single().retry_count)
    }

    @Test
    fun flushQueue_clientErrorOtherThanConflict_isRetriedNotDropped() = runTest {
        handler = { respond("", HttpStatusCode.Unauthorized) }
        enqueueResponse("e1")

        val results = manager.flushQueue()

        assertEquals("HTTP 401", assertIs<FlushResult.Error>(results.single()).message)
        assertEquals(1, pendingRows().size)
    }

    @Test
    fun flushQueue_networkException_keepsMutationAndReportsMessage() = runTest {
        handler = { throw java.io.IOException("no route to host") }
        enqueueResponse("e1")

        val results = manager.flushQueue()

        assertEquals("no route to host", assertIs<FlushResult.Error>(results.single()).message)
        assertEquals(1L, pendingRows().single().retry_count)
    }

    @Test
    fun flushQueue_failureOfOneMutationDoesNotBlockTheRest() = runTest {
        handler = { request ->
            if (request.path().endsWith("e1/attendance/me")) respond("", HttpStatusCode.BadGateway)
            else respond("", HttpStatusCode.OK)
        }
        enqueueResponse("e1")
        enqueueResponse("e2")

        val results = manager.flushQueue()

        assertIs<FlushResult.Error>(results[0])
        assertIs<FlushResult.Success>(results[1])
        assertEquals(listOf("/events/e1/attendance/me"), pendingRows().map { it.endpoint })
    }

    @Test
    fun flushQueue_afterReconnect_retriesFailedMutationAndClearsIt() = runTest {
        handler = { throw java.io.IOException("offline") }
        enqueueResponse("e1")
        manager.flushQueue()
        assertTrue(manager.hasPendingMutations())

        handler = { respond("", HttpStatusCode.OK) }
        val results = manager.flushQueue()

        assertIs<FlushResult.Success>(results.single())
        assertFalse(manager.hasPendingMutations())
        assertEquals(2, sent.size)
    }

    @Test
    fun flushQueue_secondFlushAfterSuccess_doesNotResendAnything() = runTest {
        enqueueResponse("e1")
        enqueueResponse("e2")

        manager.flushQueue()
        val second = manager.flushQueue()

        assertTrue(second.isEmpty())
        assertEquals(2, sent.size)
    }

    @Test
    fun enqueueWhileOffline_thenFlushOnReconnect_sendsEachOnce() = runTest {
        handler = { throw java.io.IOException("offline") }
        enqueueResponse("e1")
        enqueueResponse("e2")
        manager.flushQueue()
        assertEquals(2, pendingRows().size)

        handler = { respond("", HttpStatusCode.OK) }
        enqueueResponse("e3")
        manager.flushQueue()

        val successfulPaths = sent.drop(2).map { it.path() }
        assertEquals(
            listOf("/events/e1/attendance/me", "/events/e2/attendance/me", "/events/e3/attendance/me"),
            successfulPaths
        )
        assertNull(pendingRows().firstOrNull())
    }

    @Test
    fun flushQueue_overlappingFlushes_sendEachMutationOnce() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        handler = {
            gate.await()
            respond("", HttpStatusCode.OK)
        }
        enqueueResponse("e1")

        val first = async(Dispatchers.Default) { manager.flushQueue() }
        val second = async(Dispatchers.Default) { manager.flushQueue() }
        delay(300)
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, sent.size)
        assertFalse(manager.hasPendingMutations())
    }
}
