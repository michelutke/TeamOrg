package ch.teamorg.routes

import ch.teamorg.domain.repositories.ClubIntegration
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.IntegrationRepository
import ch.teamorg.infra.InvalidApiKeyException
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.middleware.requireClubRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.*

@Serializable
data class SwissVolleyKeyRequest(val apiKey: String)

@Serializable
data class SwissVolleyStatusResponse(
    val provider: String,
    val keyValid: Boolean?,
    val lastValidatedAt: String?,
    val syncPausedReason: String?
)

private fun ClubIntegration.toStatusResponse() = SwissVolleyStatusResponse(
    provider = provider,
    keyValid = keyValid,
    lastValidatedAt = lastValidatedAt,
    syncPausedReason = syncPausedReason
)

fun Route.integrationRoutes() {
    val clubRepository by inject<ClubRepository>()
    val integrationRepository by inject<IntegrationRepository>()
    val swissVolleyClient by inject<SwissVolleyClient>()

    authenticate("jwt") {
        route("/clubs/{clubId}/integrations/swissvolley") {
            put {
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@put

                val request = call.receive<SwissVolleyKeyRequest>()
                if (request.apiKey.isBlank()) {
                    return@put call.respond(HttpStatusCode.BadRequest, "API key is required")
                }

                val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, "Invalid token")

                try {
                    swissVolleyClient.listTeams(request.apiKey)
                } catch (ex: InvalidApiKeyException) {
                    return@put call.respond(HttpStatusCode.UnprocessableEntity, "Invalid SwissVolley API key")
                }

                integrationRepository.upsertKey(clubId, request.apiKey, callerId)
                val integration = integrationRepository.setKeyValidity(
                    clubId = clubId,
                    valid = true,
                    validatedAt = Instant.now(),
                    pausedReason = null
                )
                call.respond(HttpStatusCode.OK, integration.toStatusResponse())
            }

            delete {
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@delete

                integrationRepository.deprecateLinksForClub(clubId)
                integrationRepository.deleteIntegration(clubId)
                call.respond(HttpStatusCode.NoContent)
            }

            get {
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@get

                val integration = integrationRepository.getIntegration(clubId)
                if (integration == null) {
                    call.respond(HttpStatusCode.NotFound, "No SwissVolley integration configured")
                } else {
                    call.respond(integration.toStatusResponse())
                }
            }

            get("/teams") {
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@get

                val integration = integrationRepository.getIntegration(clubId)
                if (integration == null || integration.keyValid != true) {
                    return@get call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")
                }
                val apiKey = integrationRepository.getApiKey(clubId)
                    ?: return@get call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")

                val teams: List<SVTeam> = try {
                    swissVolleyClient.listTeams(apiKey)
                } catch (ex: InvalidApiKeyException) {
                    integrationRepository.setKeyValidity(
                        clubId = clubId,
                        valid = false,
                        validatedAt = Instant.now(),
                        pausedReason = "API key rejected by SwissVolley"
                    )
                    return@get call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")
                }
                call.respond(teams)
            }
        }
    }
}
