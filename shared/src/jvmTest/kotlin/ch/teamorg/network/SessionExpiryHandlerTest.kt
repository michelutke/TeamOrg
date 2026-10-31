package ch.teamorg.network

import ch.teamorg.auth.SessionExpiredNotifier
import ch.teamorg.crash.RecordingCrashReporting
import ch.teamorg.data.PushBridge
import ch.teamorg.data.network.installSessionExpiryHandler
import ch.teamorg.preferences.UserPreferences
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiryHandlerTest {

    private class RecordingPush : PushBridge {
        var logoutCount = 0
        override fun login(userId: String) = Unit
        override fun logout() {
            logoutCount++
        }
    }

    private class Fixture(status: HttpStatusCode) {
        val prefs = UserPreferences(MapSettings()).also { it.saveToken("token123") }
        val notifier = SessionExpiredNotifier()
        val push = RecordingPush()
        val crash = RecordingCrashReporting()
        val client = HttpClient(MockEngine { respond(content = "", status = status) }) {
            installSessionExpiryHandler(prefs, notifier, push, crash)
        }
        val events = mutableListOf<Unit>()

        fun collectEvents(scope: CoroutineScope) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { notifier.events.collect { events += it } }
        }
    }

    @Test
    fun `401 on an authenticated call clears the session and emits the event`() = runTest {
        val f = Fixture(HttpStatusCode.Unauthorized)
        f.collectEvents(backgroundScope)

        f.client.get("/events") { bearerAuth("token123") }

        runCurrent()

        assertNull(f.prefs.getToken())
        assertEquals(1, f.push.logoutCount)
        assertEquals(listOf("clearUser"), f.crash.calls)
        assertEquals(1, f.events.size)
    }

    @Test
    fun `non 401 responses keep the session`() = runTest {
        val f = Fixture(HttpStatusCode.Forbidden)
        f.collectEvents(backgroundScope)

        f.client.get("/events") { bearerAuth("token123") }

        assertNotNull(f.prefs.getToken())
        assertEquals(0, f.push.logoutCount)
        assertEquals(0, f.events.size)
    }

    @Test
    fun `401 without a bearer token does not trigger`() = runTest {
        val f = Fixture(HttpStatusCode.Unauthorized)
        f.collectEvents(backgroundScope)

        f.client.get("/events")

        assertNotNull(f.prefs.getToken())
        assertEquals(0, f.events.size)
    }

    @Test
    fun `credential check endpoints do not trigger on 401`() = runTest {
        val f = Fixture(HttpStatusCode.Unauthorized)
        f.collectEvents(backgroundScope)

        f.client.post("/auth/login") { bearerAuth("token123") }
        f.client.post("/auth/register") { bearerAuth("token123") }
        f.client.post("/auth/change-password") { bearerAuth("token123") }
        f.client.delete("/auth/me") { bearerAuth("token123") }

        assertNotNull(f.prefs.getToken())
        assertEquals(0, f.push.logoutCount)
        assertEquals(0, f.events.size)
    }

    @Test
    fun `stale 401 after re-login keeps the new token`() = runTest {
        val f = Fixture(HttpStatusCode.Unauthorized)
        f.collectEvents(backgroundScope)
        f.prefs.saveToken("new-token")

        f.client.get("/events") { bearerAuth("token123") }
        runCurrent()

        assertEquals("new-token", f.prefs.getToken())
        assertEquals(0, f.push.logoutCount)
        assertEquals(0, f.events.size)
    }

    @Test
    fun `GET auth me on 401 still counts as an expired session`() = runTest {
        val f = Fixture(HttpStatusCode.Unauthorized)
        f.collectEvents(backgroundScope)

        f.client.get("/auth/me") { bearerAuth("token123") }
        runCurrent()

        assertNull(f.prefs.getToken())
        assertEquals(1, f.events.size)
    }
}
