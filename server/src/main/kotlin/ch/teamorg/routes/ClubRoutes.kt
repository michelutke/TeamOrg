package ch.teamorg.routes

import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.IntegrationRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.middleware.authenticateUser
import ch.teamorg.middleware.requireClubMember
import ch.teamorg.middleware.requireClubRole
import ch.teamorg.storage.FileStorageService
import ch.teamorg.storage.FileType
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.*

@Serializable
data class CreateClubRequest(val name: String, val sportType: String = "volleyball", val location: String? = null)

@Serializable
data class UpdateClubRequest(val name: String? = null, val location: String? = null)

@Serializable
data class MigrateTeamRequest(val targetTeamId: String)

@Serializable
data class MigrateTeamResponse(val movedMembers: Int, val targetTeamId: String)

fun Route.clubRoutes() {
    val clubRepository by inject<ClubRepository>()
    val userRepository by inject<UserRepository>()
    val teamRepository by inject<TeamRepository>()
    val integrationRepository by inject<IntegrationRepository>()
    val fileStorageService by inject<FileStorageService>()

    authenticate("jwt") {
        route("/clubs") {
            post {
                call.authenticateUser(userRepository) { user ->
                    if (!user.isSuperAdmin) {
                        return@authenticateUser call.respond(HttpStatusCode.Forbidden, "Only super-admins can create clubs")
                    }
                    val request = call.receive<CreateClubRequest>()
                    if (request.name.isBlank()) {
                        return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Club name is required")
                    }
                    val club = clubRepository.create(
                        name = request.name,
                        sportType = request.sportType,
                        location = request.location,
                        creatorUserId = UUID.fromString(user.id)
                    )
                    call.respond(HttpStatusCode.Created, club)
                }
            }

            route("/{clubId}") {
                get {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubMember(clubId, clubRepository)) return@get
                    val club = clubRepository.findById(clubId)
                    if (club == null) {
                        call.respond(HttpStatusCode.NotFound, "Club not found")
                    } else {
                        call.respond(club)
                    }
                }

                patch {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@patch

                    val request = call.receive<UpdateClubRequest>()
                    val club = clubRepository.update(clubId, request.name, request.location, null)
                    call.respond(club)
                }

                post("/logo") {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

                    val multipart = call.receiveMultipart()
                    var fileBytes: ByteArray? = null
                    var extension: String? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val contentType = part.contentType
                            if (contentType == null || !listOf("image/jpeg", "image/png", "image/webp").contains(contentType.toString())) {
                                part.dispose()
                                return@forEachPart
                            }

                            extension = when (contentType.toString()) {
                                "image/jpeg" -> "jpg"
                                "image/png" -> "png"
                                "image/webp" -> "webp"
                                else -> "bin"
                            }

                            fileBytes = part.streamProvider().readBytes()
                        }
                        part.dispose()
                    }

                    if (fileBytes == null) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Logo file is required (jpg/png/webp)")
                    }

                    if (fileBytes!!.size > 2 * 1024 * 1024) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Logo file size must be less than 2MB")
                    }

                    val path = fileStorageService.save(fileBytes!!, FileType.CLUB_LOGO, extension!!)
                    // Store the publicly-servable URL (mirror avatar handling in AuthRoutes,
                    // which persists "/uploads/$path"); files are served from static("/uploads").
                    val club = clubRepository.update(clubId, null, null, "/uploads/$path")
                    call.respond(club)
                }

                get("/teams") {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubMember(clubId, clubRepository)) return@get
                    val teams = clubRepository.listTeams(clubId)
                    call.respond(teams)
                }

                // Moved from TeamRoutes to match hierarchy /clubs/{clubId}/teams
                post("/teams") {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

                    val request = call.receive<CreateTeamRequest>()
                    if (request.name.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Team name is required")
                    }
                    val team = teamRepository.create(clubId, request.name, request.description)
                    call.respond(HttpStatusCode.Created, team)
                }

                // Migrate a deprecated team into a live SV-linked successor (season rollover, §14).
                post("/teams/{teamId}/migrate-to") {
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

                    val sourceTeamId = UUID.fromString(call.parameters["teamId"])
                    val request = call.receive<MigrateTeamRequest>()
                    val targetTeamId = try {
                        UUID.fromString(request.targetTeamId)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetTeamId")
                    }

                    if (sourceTeamId == targetTeamId) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Source and target must differ")
                    }

                    val source = teamRepository.findById(sourceTeamId)
                    val target = teamRepository.findById(targetTeamId)
                    if (source == null || target == null) {
                        return@post call.respond(HttpStatusCode.NotFound, "Team not found")
                    }
                    if (source.clubId != clubId.toString() || target.clubId != clubId.toString()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Both teams must belong to the club")
                    }

                    val sourceLinks = integrationRepository.listLinksForTeam(sourceTeamId)
                    val sourceDeprecated = source.archivedAt != null ||
                        (sourceLinks.isNotEmpty() && sourceLinks.all { it.deprecatedAt != null })
                    if (!sourceDeprecated) {
                        return@post call.respond(HttpStatusCode.Conflict, "Source team is not deprecated")
                    }

                    val targetLive = target.archivedAt == null &&
                        integrationRepository.listLinksForTeam(targetTeamId).any { it.deprecatedAt == null }
                    if (!targetLive) {
                        return@post call.respond(HttpStatusCode.UnprocessableEntity, "Target team is not a live SwissVolley-linked team")
                    }

                    val movedMembers = teamRepository.migrateTeam(sourceTeamId, targetTeamId)
                    call.respond(
                        HttpStatusCode.OK,
                        MigrateTeamResponse(movedMembers, targetTeamId.toString())
                    )
                }
            }
        }
    }
}
