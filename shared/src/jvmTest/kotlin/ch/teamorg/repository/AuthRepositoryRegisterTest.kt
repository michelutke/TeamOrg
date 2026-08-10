package ch.teamorg.repository

import ch.teamorg.data.repository.AuthRepositoryImpl
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryRegisterTest {

    private fun repositoryReturning(
        status: HttpStatusCode,
        body: String = ""
    ): Pair<AuthRepositoryImpl, UserPreferences> {
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
        val prefs = UserPreferences(MapSettings())
        return AuthRepositoryImpl(client, prefs) to prefs
    }

    private val request = RegisterRequest("coach@example.com", "password123", "Coach")

    @Test
    fun register_on409_returnsEmailAlreadyRegistered() = runTest {
        val (repository, prefs) = repositoryReturning(
            HttpStatusCode.Conflict,
            "\"Email already registered\""
        )

        val result = repository.register(request)

        assertIs<EmailAlreadyRegisteredException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("already exists"))
        assertEquals(null, prefs.getToken())
    }

    @Test
    fun register_on400_returnsGenericFailure() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.BadRequest)

        val error = repository.register(request).exceptionOrNull()

        assertTrue(error !is EmailAlreadyRegisteredException)
        assertTrue(error?.message.orEmpty().contains("Registration failed"))
    }

    @Test
    fun register_on200_savesTokenAndSucceeds() = runTest {
        val (repository, prefs) = repositoryReturning(
            HttpStatusCode.OK,
            """{"token":"jwt123","userId":"user-1","displayName":"Coach","avatarUrl":null}"""
        )

        val result = repository.register(request)

        assertTrue(result.isSuccess)
        assertEquals("jwt123", prefs.getToken())
    }
}
