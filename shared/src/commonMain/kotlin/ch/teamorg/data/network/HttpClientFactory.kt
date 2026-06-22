package ch.teamorg.data.network

import ch.teamorg.preferences.UserPreferences
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    fun create(userPreferences: UserPreferences): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }

            if (ApiConfig.enableNetworkLogging) {
                install(Logging) {
                    level = LogLevel.ALL
                }
            }

            install(DefaultRequest) {
                url(ApiConfig.baseUrl)
                contentType(ContentType.Application.Json)
            }
        }.also { client ->
            client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                val token = userPreferences.getToken()
                if (token != null) {
                    context.bearerAuth(token)
                }
            }
        }
    }
}
