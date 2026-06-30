package ch.teamorg.routes

import ch.teamorg.domain.models.TeamAppearance
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.IntegrationRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.infra.SwissVolleySyncService
import ch.teamorg.middleware.authenticateUser
import ch.teamorg.middleware.requireTeamRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.*

@Serializable
data class CreateTeamRequest(val name: String, val description: String? = null)

@Serializable
data class UpdateTeamRequest(
    val name: String? = null,
    val description: String? = null,
    val appearance: TeamAppearance? = null
)

// M3 Expressive shape set offered by the team-appearance picker.
private val ALLOWED_SHAPES = setOf("cookie", "clover", "sunny", "flower")
private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

@Serializable
data class UpdateRoleRequest(val role: String)

@Serializable
data class AddMemberRequest(val userId: String, val role: String)

@Serializable
data class UpdateProfileRequest(val jerseyNumber: Int? = null, val position: String? = null)

@Serializable
data class GameSyncRequest(val enabled: Boolean)

@Serializable
data class GameSyncResponse(val enabled: Boolean)

fun Route.teamRoutes() {
    val teamRepository by inject<TeamRepository>()
    val userRepository by inject<UserRepository>()
    val integrationRepository by inject<IntegrationRepository>()
    val eventRepository by inject<EventRepository>()
    val syncService by inject<SwissVolleySyncService>()

    authenticate("jwt") {
        route("/teams") {
            route("/{teamId}") {
                get {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
                    val team = teamRepository.findById(teamId)
                    if (team == null) {
                        call.respond(HttpStatusCode.NotFound, "Team not found")
                    } else {
                        call.respond(team)
                    }
                }

                patch {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@patch

                    val request = call.receive<UpdateTeamRequest>()
                    val appearance = request.appearance
                    if (appearance != null) {
                        if (appearance.shape !in ALLOWED_SHAPES || !HEX_COLOR.matches(appearance.color)) {
                            return@patch call.respond(HttpStatusCode.BadRequest, "Invalid appearance")
                        }
                    }
                    val team = teamRepository.update(
                        teamId,
                        request.name,
                        request.description,
                        appearance?.shape,
                        appearance?.color
                    )
                    call.respond(team)
                }

                patch("/game-sync") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@patch

                    val request = call.receive<GameSyncRequest>()

                    val hasActiveLink = integrationRepository.listLinksForTeam(teamId)
                        .any { it.deprecatedAt == null }
                    if (!hasActiveLink) {
                        return@patch call.respond(
                            HttpStatusCode.Conflict,
                            "Team is not linked to SwissVolley"
                        )
                    }

                    teamRepository.setGamesSyncEnabled(teamId, request.enabled)
                    if (request.enabled) {
                        syncService.syncTeam(teamId)
                    }
                    call.respond(HttpStatusCode.OK, GameSyncResponse(request.enabled))
                }

                delete {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    // archive only, and only club_manager
                    if (!call.requireTeamRole(teamId, "club_manager", teamRepository = teamRepository)) return@delete

                    val team = teamRepository.archive(teamId)
                    call.respond(team)
                }

                get("/importable-series") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get
                    call.respond(eventRepository.listImportableSeries(teamId))
                }

                get("/members") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
                    val members = teamRepository.listMembers(teamId)
                    call.respond(members)
                }

                post("/members") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "club_manager", teamRepository = teamRepository)) return@post
                    val body = call.receive<AddMemberRequest>()
                    if (body.role !in listOf("player", "coach"))
                        return@post call.respond(HttpStatusCode.BadRequest, "Invalid role")
                    val userId = runCatching { UUID.fromString(body.userId) }.getOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid userId")
                    if (userRepository.findById(userId) == null)
                        return@post call.respond(HttpStatusCode.NotFound, "User not found")
                    call.respond(teamRepository.addMember(teamId, userId, body.role))
                }

                patch("/members/{userId}/role") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "club_manager", teamRepository = teamRepository)) return@patch
                    val userId = UUID.fromString(call.parameters["userId"])
                    val request = call.receive<UpdateRoleRequest>()
                    val member = teamRepository.updateMemberRole(teamId, userId, request.role)
                    call.respond(member)
                }

                delete("/members/{userId}") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "club_manager", teamRepository = teamRepository)) return@delete
                    val userId = UUID.fromString(call.parameters["userId"])
                    teamRepository.removeMember(teamId, userId)
                    call.respond(HttpStatusCode.NoContent)
                }

                patch("/members/{userId}/profile") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    val userId = UUID.fromString(call.parameters["userId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@patch
                    val request = call.receive<UpdateProfileRequest>()
                    val member = teamRepository.updateMemberProfile(teamId, userId, request.jerseyNumber, request.position)
                    call.respond(member)
                }

                delete("/leave") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    call.authenticateUser(userRepository) { user ->
                        teamRepository.removeMember(teamId, UUID.fromString(user.id))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        // Self-service profile edit: a member may update only their OWN jersey/position.
        // The coach/manager route (/teams/{id}/members/{userId}/profile) stays restricted.
        put("/users/me/teams/{teamId}/profile") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            val request = call.receive<UpdateProfileRequest>()
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                if (!teamRepository.hasRole(userId, teamId, "coach", "player", "club_manager")) {
                    return@authenticateUser call.respond(
                        HttpStatusCode.Forbidden,
                        "Not a member of this team"
                    )
                }
                val member = teamRepository.updateMemberProfile(
                    teamId,
                    userId,
                    request.jerseyNumber,
                    request.position
                )
                call.respond(member)
            }
        }
    }
}
