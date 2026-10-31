package ch.teamorg.data.network

import ch.teamorg.auth.SessionExpiredNotifier
import ch.teamorg.data.PushBridge
import ch.teamorg.data.PushRegistration
import ch.teamorg.preferences.UserPreferences
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val LOCAL_DEV_HOSTS = listOf("10.0.2.2", "localhost", "127.0.0.1")

/**
 * A release build must never transmit user data over cleartext. `API_BASE_URL` is injected at
 * build time, so a wrong value would otherwise ship silently — this turns that into a crash at
 * client construction. Local development hosts stay allowed over http.
 */
internal fun requireSecureBaseUrl(baseUrl: String): String {
    if (baseUrl.startsWith("https://")) return baseUrl
    // Anchor on the host boundary so "localhost.evil.example" is not mistaken for localhost.
    val isLocalDev = LOCAL_DEV_HOSTS.any { host ->
        baseUrl == "http://$host" ||
            baseUrl.startsWith("http://$host:") ||
            baseUrl.startsWith("http://$host/")
    }
    require(isLocalDev) { "API_BASE_URL must use https:// (got: $baseUrl)" }
    return baseUrl
}

// These endpoints answer 401 for wrong credentials, not for an expired session.
private val CREDENTIAL_CHECK_PATHS = setOf("/auth/login", "/auth/register", "/auth/change-password")
private const val ACCOUNT_PATH = "/auth/me"

private fun isCredentialCheck(method: HttpMethod, path: String): Boolean =
    path in CREDENTIAL_CHECK_PATHS || (method == HttpMethod.Delete && path == ACCOUNT_PATH)

internal fun HttpClientConfig<*>.installSessionExpiryHandler(
    userPreferences: UserPreferences,
    notifier: SessionExpiredNotifier,
    push: PushBridge
) {
    HttpResponseValidator {
        validateResponse { response ->
            val request = response.call.request
            val currentToken = userPreferences.getToken()
            val wasAuthenticated = currentToken != null &&
                request.headers[HttpHeaders.Authorization] == "Bearer $currentToken"
            if (response.status == HttpStatusCode.Unauthorized &&
                wasAuthenticated &&
                !isCredentialCheck(request.method, request.url.encodedPath)
            ) {
                push.logout()
                userPreferences.clearToken()
                notifier.notifyExpired()
            }
        }
    }
}

object HttpClientFactory {
    fun create(
        userPreferences: UserPreferences,
        sessionExpiredNotifier: SessionExpiredNotifier,
        push: PushBridge = PushRegistration
    ): HttpClient {
        return HttpClient {
            installSessionExpiryHandler(userPreferences, sessionExpiredNotifier, push)

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
                url(requireSecureBaseUrl(ApiConfig.baseUrl))
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
