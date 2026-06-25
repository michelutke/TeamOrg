package ch.teamorg.routes

import ch.teamorg.domain.repositories.ClubIntegration
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.IntegrationRepository
import ch.teamorg.domain.repositories.TeamRepository
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

@Serializable
data class SwissVolleyImportRequest(val svTeamIds: List<Int>)

@Serializable
data class ImportedTeam(val teamId: String, val svTeamId: Int, val name: String)

@Serializable
data class SwissVolleyImportResponse(
    val created: List<ImportedTeam>,
    val skipped: List<Int>
)

fun Route.integrationRoutes() {
    val clubRepository by inject<ClubRepository>()
    val integrationRepository by inject<IntegrationRepository>()
    val swissVolleyClient by inject<SwissVolleyClient>()
    val teamRepository by inject<TeamRepository>()

    authenticate("jwt") {
        route("/clubs/{clubId}/teams/import") {
            post {
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

                val request = call.receive<SwissVolleyImportRequest>()

                val integration = integrationRepository.getIntegration(clubId)
                if (integration == null || integration.keyValid != true) {
                    return@post call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")
                }
                val apiKey = integrationRepository.getApiKey(clubId)
                    ?: return@post call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")

                val teams: List<SVTeam> = try {
                    swissVolleyClient.listTeams(apiKey)
                } catch (ex: InvalidApiKeyException) {
                    integrationRepository.setKeyValidity(
                        clubId = clubId,
                        valid = false,
                        validatedAt = Instant.now(),
                        pausedReason = "API key rejected by SwissVolley"
                    )
                    return@post call.respond(HttpStatusCode.Conflict, "No valid SwissVolley API key stored")
                }

                val byTeamId = teams.mapNotNull { team -> team.teamId?.let { it to team } }.toMap()
                val alreadyLinked = integrationRepository.listLinkedSvTeamIdsForClub(clubId).toMutableSet()

                val created = mutableListOf<ImportedTeam>()
                val skipped = mutableListOf<Int>()

                for (svTeamId in request.svTeamIds) {
                    val svTeam = byTeamId[svTeamId]
                    if (svTeam == null || svTeamId in alreadyLinked) {
                        skipped.add(svTeamId)
                        continue
                    }

                    val name = svTeam.caption ?: "SwissVolley Team $svTeamId"
                    val team = teamRepository.create(clubId, name, null)
                    integrationRepository.createLink(
                        teamId = UUID.fromString(team.id),
                        svTeamId = svTeamId,
                        svSeasonalTeamId = svTeam.seasonalTeamId,
                        svLeagueCaption = svTeam.league?.caption,
                        svGender = svTeam.gender
                    )
                    alreadyLinked.add(svTeamId)
                    created.add(ImportedTeam(teamId = team.id, svTeamId = svTeamId, name = name))
                }

                call.respond(
                    HttpStatusCode.OK,
                    SwissVolleyImportResponse(created = created, skipped = skipped)
                )
            }
        }

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
