package ch.teamorg.repository

import ch.teamorg.data.repository.TeamRepositoryImpl
import ch.teamorg.domain.LinkMemberResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Maps the /link endpoint's status codes onto [LinkMemberResult]. The endpoint returns 409 for two
 * distinct causes (target already holds another roster row of the team; this row is already held by a real
 * account) — mobile collapses both, since the coach's next action is the same either way.
 */
class TeamRepositoryLinkResultTest {

    private fun repoReturning(status: HttpStatusCode, body: String = ""): TeamRepositoryImpl {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return TeamRepositoryImpl(client)
    }

    @Test
    fun linkNdsMember_on200_returnsSuccess() = runTest {
        val result = repoReturning(HttpStatusCode.OK, "{}").linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.Success, result)
    }

    @Test
    fun linkNdsMember_on409_returnsConflict() = runTest {
        val result = repoReturning(HttpStatusCode.Conflict, "\"already linked\"")
            .linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.Conflict, result)
    }

    @Test
    fun linkNdsMember_on400_returnsNotLinkable() = runTest {
        val result = repoReturning(HttpStatusCode.BadRequest, "\"not linkable\"")
            .linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.NotLinkable, result)
    }

    @Test
    fun linkNdsMember_onOtherStatus_returnsError() = runTest {
        val result = repoReturning(HttpStatusCode.NotFound, "\"nope\"").linkNdsMember("t1", "m1", "u1")
        assertTrue(result is LinkMemberResult.Error, "expected Error, got $result")
    }

    @Test
    fun getDuplicateSuggestions_parsesTheServerShape() = runTest {
        val body = """
            [{"memberId":"m1","lastName":"Müller","firstName":"Lara","birthDate":"2008-04-01",
              "personNumber":"123456789","funktion":"Teilnehmer/in",
              "candidates":[{"userId":"u1","displayName":"Lara Müller","score":"HIGH"}],
              "willMove":{"attendance":12,"subgroups":1,"rules":0}}]
        """.trimIndent()
        val result = repoReturning(HttpStatusCode.OK, body).getDuplicateSuggestions("t1")
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals("m1", suggestions[0].memberId)
        assertEquals("Lara", suggestions[0].firstName)
        assertEquals(12, suggestions[0].willMove.attendance)
        assertEquals("HIGH", suggestions[0].candidates.single().score)
    }

    @Test
    fun getDuplicateSuggestions_onEmptyArray_returnsEmptyList() = runTest {
        val result = repoReturning(HttpStatusCode.OK, "[]").getDuplicateSuggestions("t1")
        assertTrue(result.getOrThrow().isEmpty())
    }
}
