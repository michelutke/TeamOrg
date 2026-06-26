package ch.teamorg.infra

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class InvalidApiKeyException(message: String = "Valid API-Key required") : RuntimeException(message)

interface SwissVolleyClient {
    suspend fun listTeams(apiKey: String): List<SVTeam>
    suspend fun listGames(apiKey: String): List<SVGame>
}

class SwissVolleyClientImpl(
    private val client: HttpClient,
    private val baseUrl: String
) : SwissVolleyClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listTeams(apiKey: String): List<SVTeam> = get(SVTeam.serializer(), "/indoor/teams", apiKey)

    override suspend fun listGames(apiKey: String): List<SVGame> = get(SVGame.serializer(), "/indoor/games", apiKey)

    private suspend fun <T> get(
        elementSerializer: kotlinx.serialization.KSerializer<T>,
        path: String,
        apiKey: String
    ): List<T> {
        val response = client.get("$baseUrl$path") {
            header("Authorization", apiKey)
        }
        val body = response.bodyAsText()
        // The SwissVolley API signals an invalid key by returning the auth-error message in the
        // body even with a 200 status (see SwissVolleyClientTest), so we must check the body on
        // any status, not just 401. A successful payload is always a JSON array; the phrase
        // appearing inside legitimate array data is not a realistic collision.
        if (response.status == HttpStatusCode.Unauthorized || body.contains("Valid API-Key required")) {
            throw InvalidApiKeyException()
        }
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(elementSerializer), body)
    }
}
