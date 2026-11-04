package ch.teamorg.repository

import ch.teamorg.data.PushBridge
import ch.teamorg.data.repository.AuthRepositoryImpl
import ch.teamorg.domain.LoginRequest
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.preferences.UserPreferences
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRepositoryPushTest {

    private class RecordingPush : PushBridge {
        val calls = mutableListOf<String>()
        override fun login(userId: String) {
            calls += "login:$userId"
        }

        override fun logout() {
            calls += "logout"
        }
    }

    private val authJson = """{"token":"t","userId":"user-1","displayName":"A","avatarUrl":null}"""
    private val meJson = """{"id":"user-1","email":"a@b.ch","displayName":"A","avatarUrl":null}"""

    private fun repository(
        status: HttpStatusCode,
        body: String,
        push: PushBridge
    ): AuthRepositoryImpl {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(DefaultRequest) { contentType(ContentType.Application.Json) }
        }
        return AuthRepositoryImpl(client, UserPreferences(MapSettings()), push)
    }

    @Test
    fun `login registers the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.OK, authJson, push).login(LoginRequest("a@b.ch", "pw"))
        assertEquals(listOf("login:user-1"), push.calls)
    }

    @Test
    fun `failed login does not register the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.Unauthorized, "", push).login(LoginRequest("a@b.ch", "pw"))
        assertEquals(emptyList(), push.calls)
    }

    @Test
    fun `register registers the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.Created, authJson, push)
            .register(RegisterRequest("a@b.ch", "pw", "A"))
        assertEquals(listOf("login:user-1"), push.calls)
    }

    @Test
    fun `session restore via getMe registers the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.OK, meJson, push).getMe()
        assertEquals(listOf("login:user-1"), push.calls)
    }

    @Test
    fun `logout clears the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.OK, "", push).logout()
        assertEquals(listOf("logout"), push.calls)
    }

    @Test
    fun `account deletion clears the push user`() = runTest {
        val push = RecordingPush()
        repository(HttpStatusCode.NoContent, "", push).deleteAccount("pw")
        assertEquals(listOf("logout"), push.calls)
    }
}
