package ch.teamorg.repository

import ch.teamorg.data.repository.AuthRepositoryImpl
import ch.teamorg.domain.DeleteAccountResult
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

class AuthRepositoryDeleteAccountTest {

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
        val prefs = UserPreferences(MapSettings()).also { it.saveToken("token123") }
        return AuthRepositoryImpl(client, prefs) to prefs
    }

    @Test
    fun `204 maps to Success and clears the stored token`() = runTest {
        val (repository, prefs) = repositoryReturning(HttpStatusCode.NoContent)
        assertEquals(DeleteAccountResult.Success, repository.deleteAccount("password123"))
        assertEquals(null, prefs.getToken())
    }

    @Test
    fun `200 maps to Success`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK)
        assertEquals(DeleteAccountResult.Success, repository.deleteAccount("password123"))
    }

    @Test
    fun `401 maps to InvalidPassword and keeps the token`() = runTest {
        val (repository, prefs) = repositoryReturning(HttpStatusCode.Unauthorized)
        assertEquals(DeleteAccountResult.InvalidPassword, repository.deleteAccount("wrong"))
        assertEquals("token123", prefs.getToken())
    }

    @Test
    fun `409 maps to OwnsClubs with the club names`() = runTest {
        val (repository, _) = repositoryReturning(
            HttpStatusCode.Conflict,
            """{"reason":"owns_clubs","clubs":["Owner Club","Second Club"]}"""
        )
        val result = repository.deleteAccount("password123")
        assertIs<DeleteAccountResult.OwnsClubs>(result)
        assertEquals(listOf("Owner Club", "Second Club"), result.clubNames)
    }

    @Test
    fun `409 with an unparseable body still maps to OwnsClubs with no names`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.Conflict, "not json")
        val result = repository.deleteAccount("password123")
        assertIs<DeleteAccountResult.OwnsClubs>(result)
        assertTrue(result.clubNames.isEmpty())
    }

    @Test
    fun `500 maps to Error`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.InternalServerError)
        assertIs<DeleteAccountResult.Error>(repository.deleteAccount("password123"))
    }
}
