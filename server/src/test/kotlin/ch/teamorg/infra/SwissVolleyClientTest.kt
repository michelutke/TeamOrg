package ch.teamorg.infra

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SwissVolleyClientTest {

    private fun clientWith(mockEngine: MockEngine): SwissVolleyClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        return SwissVolleyClientImpl(httpClient, baseUrl = "https://api.test.invalid")
    }

    @Test
    fun `listTeams parses canned JSON and ignores unknown fields`() {
        // Realistic minimal payload with extra unknown fields to prove ignoreUnknownKeys.
        val body = """
            [
              {
                "teamId": 12345,
                "seasonalTeamId": 67890,
                "caption": "VBC Thun H1",
                "gender": "male",
                "league": { "leagueId": 7, "caption": "NLA", "extraLeagueField": "ignored" },
                "club": { "clubId": 42, "clubCaption": "VBC Thun" },
                "iCalUrl": "https://api.test.invalid/cal/12345.ics",
                "unexpectedTopLevel": { "nested": true }
              }
            ]
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val svClient = clientWith(mockEngine)

        val teams = runBlocking { svClient.listTeams("any-key") }

        assertEquals(1, teams.size, "Exactly one team should be parsed")
        val team = teams[0]
        assertEquals(12345, team.teamId)
        assertEquals(67890, team.seasonalTeamId)
        assertEquals("VBC Thun H1", team.caption)
        assertEquals("male", team.gender)
        assertEquals("NLA", team.league?.caption)
        assertEquals("VBC Thun", team.club?.clubCaption)
    }

    @Test
    fun `listGames parses canned JSON and ignores unknown fields`() {
        // Realistic minimal payload with extra unknown fields to prove ignoreUnknownKeys.
        val body = """
            [
              {
                "gameId": 998877,
                "playDate": "2025-10-04T18:00:00",
                "playDateUtc": "2025-10-04T16:00:00Z",
                "status": 1,
                "teams": {
                  "home": { "teamId": 12345, "caption": "VBC Thun H1", "clubId": 42, "clubCaption": "VBC Thun" },
                  "away": { "teamId": 54321, "caption": "Volley Luzern", "clubId": 99 }
                },
                "hall": { "hallId": 5, "caption": "Lachenhalle", "city": "Thun", "geo": { "lat": 46.7 } },
                "setResults": [ { "home": 25, "away": 20, "duration": 28 } ],
                "resultSummary": { "winner": "home", "wonSetsHomeTeam": 3, "wonSetsAwayTeam": 1 },
                "referees": [ { "name": "Unknown Field Object" } ]
              }
            ]
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val svClient = clientWith(mockEngine)

        val games = runBlocking { svClient.listGames("any-key") }

        assertEquals(1, games.size, "Exactly one game should be parsed")
        val game = games[0]
        assertEquals(998877, game.gameId)
        assertEquals("2025-10-04T16:00:00Z", game.playDateUtc)
        assertEquals(1, game.status)
        assertEquals(12345, game.teams?.home?.teamId)
        assertEquals("Volley Luzern", game.teams?.away?.caption)
        assertEquals("Lachenhalle", game.hall?.caption)
        assertEquals(1, game.setResults?.size)
        assertEquals(25, game.setResults?.get(0)?.home)
        assertEquals("home", game.resultSummary?.winner)
    }

    @Test
    fun `401 with Valid API-Key required body throws InvalidApiKeyException`() {
        val mockEngine = MockEngine {
            respond(
                content = "Valid API-Key required",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val svClient = clientWith(mockEngine)

        assertFailsWith<InvalidApiKeyException> {
            runBlocking { svClient.listTeams("bad-key") }
        }
    }

    @Test
    fun `Valid API-Key required body with 200 status still throws InvalidApiKeyException`() {
        // SwissVolley returns the rejection message in the body even with a 200; client must detect it.
        val mockEngine = MockEngine {
            respond(
                content = "Valid API-Key required",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val svClient = clientWith(mockEngine)

        assertFailsWith<InvalidApiKeyException> {
            runBlocking { svClient.listGames("bad-key") }
        }
    }

    @Test
    fun `sends apiKey as Authorization header`() {
        var capturedAuth: String? = null
        var capturedUrl: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            capturedUrl = request.url.toString()
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val svClient = clientWith(mockEngine)

        val teams = runBlocking { svClient.listTeams("my-secret-key") }

        assertEquals(0, teams.size, "Empty array should parse to empty list")
        assertNotNull(capturedAuth, "MockEngine should have been called")
        assertEquals("my-secret-key", capturedAuth, "API key must be sent verbatim as Authorization header")
        assertEquals("https://api.test.invalid/indoor/teams", capturedUrl)
    }
}
