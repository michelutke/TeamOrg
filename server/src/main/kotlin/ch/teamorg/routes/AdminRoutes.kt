package ch.teamorg.routes

import ch.teamorg.domain.repositories.AdminRepository
import ch.teamorg.domain.repositories.AuditLogRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.middleware.requireSuperAdmin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID

@Serializable
data class CreateClubAdminRequest(
    val name: String,
    val sportType: String? = null,
    val location: String? = null,
    val managerEmail: String? = null
)

@Serializable
data class UpdateClubAdminRequest(
    val name: String? = null,
    val location: String? = null,
    val sportType: String? = null
)

@Serializable
data class AddManagerRequest(val email: String)

fun Route.adminRoutes() {
    val adminRepository by inject<AdminRepository>()
    val auditLogRepository by inject<AuditLogRepository>()
    val userRepository by inject<UserRepository>()

    authenticate("jwt") {
        route("/admin") {
            // GET /admin/stats — Dashboard statistics
            get("/stats") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val stats = adminRepository.getDashboardStats()
                    call.respond(stats)
                }
            }

            // GET /admin/clubs?page=1&pageSize=50 — List all clubs
            get("/clubs") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val page = call.parameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50
                    val clubs = adminRepository.findAllClubs(page, pageSize)
                    call.respond(clubs)
                }
            }

            // GET /admin/clubs/{clubId} — Club detail with managers
            get("/clubs/{clubId}") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    val club = adminRepository.findClubWithManagers(clubId)
                    if (club == null) {
                        call.respond(HttpStatusCode.NotFound, "Club not found")
                    } else {
                        call.respond(club)
                    }
                }
            }

            // POST /admin/clubs — Create club + optionally assign ClubManager (SA-01)
            post("/clubs") {
                call.requireSuperAdmin(userRepository) { user ->
                    val request = call.receive<CreateClubAdminRequest>()
                    if (request.name.isBlank()) {
                        return@requireSuperAdmin call.respond(HttpStatusCode.BadRequest, "Club name required")
                    }

                    val club = adminRepository.createClub(
                        name = request.name,
                        sportType = request.sportType ?: "volleyball",
                        location = request.location
                    )

                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.create",
                        targetType = "club",
                        targetId = club.id,
                        details = """{"name":"${request.name}"}"""
                    )

                    if (!request.managerEmail.isNullOrBlank()) {
                        val manager = userRepository.findByEmail(request.managerEmail)
                        if (manager != null) {
                            adminRepository.addClubManager(UUID.fromString(club.id), UUID.fromString(manager.id))
                            auditLogRepository.log(
                                actorId = UUID.fromString(user.id),
                                actorEmail = user.email,
                                action = "club.manager.add",
                                targetType = "club",
                                targetId = club.id,
                                details = """{"managerUserId":"${manager.id}","managerEmail":"${request.managerEmail}"}"""
                            )
                        }
                    }

                    call.respond(HttpStatusCode.Created, club)
                }
            }

            // PATCH /admin/clubs/{clubId} — Edit club details (SA-04)
            patch("/clubs/{clubId}") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    val request = call.receive<UpdateClubAdminRequest>()
                    adminRepository.updateClub(clubId, request.name, request.location, request.sportType)
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.update",
                        targetType = "club",
                        targetId = clubId.toString(),
                        details = """{"name":"${request.name}","location":"${request.location}"}"""
                    )
                    val updated = adminRepository.findClubWithManagers(clubId)
                    if (updated == null) {
                        call.respond(HttpStatusCode.NotFound, "Club not found")
                    } else {
                        call.respond(updated)
                    }
                }
            }

            // POST /admin/clubs/{clubId}/deactivate (SA-02)
            post("/clubs/{clubId}/deactivate") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    adminRepository.deactivateClub(clubId)
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.deactivate",
                        targetType = "club",
                        targetId = clubId.toString()
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "deactivated"))
                }
            }

            // POST /admin/clubs/{clubId}/reactivate (SA-02)
            post("/clubs/{clubId}/reactivate") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    adminRepository.reactivateClub(clubId)
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.reactivate",
                        targetType = "club",
                        targetId = clubId.toString()
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "active"))
                }
            }

            // DELETE /admin/clubs/{clubId} — Soft delete (SA-05)
            delete("/clubs/{clubId}") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    adminRepository.deleteClub(clubId)
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.delete",
                        targetType = "club",
                        targetId = clubId.toString()
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                }
            }

            // POST /admin/clubs/{clubId}/managers — Add ClubManager (SA-06)
            post("/clubs/{clubId}/managers") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    val request = call.receive<AddManagerRequest>()
                    val manager = userRepository.findByEmail(request.email)
                    if (manager == null) {
                        return@requireSuperAdmin call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                    adminRepository.addClubManager(clubId, UUID.fromString(manager.id))
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.manager.add",
                        targetType = "club",
                        targetId = clubId.toString(),
                        details = """{"managerUserId":"${manager.id}"}"""
                    )
                    call.respond(HttpStatusCode.Created, mapOf("status" to "added"))
                }
            }

            // DELETE /admin/clubs/{clubId}/managers/{userId} — Remove ClubManager (SA-07)
            delete("/clubs/{clubId}/managers/{userId}") {
                call.requireSuperAdmin(userRepository) { user ->
                    val clubId = UUID.fromString(call.parameters["clubId"])
                    val targetUserId = UUID.fromString(call.parameters["userId"])
                    adminRepository.removeClubManager(clubId, targetUserId)
                    auditLogRepository.log(
                        actorId = UUID.fromString(user.id),
                        actorEmail = user.email,
                        action = "club.manager.remove",
                        targetType = "club",
                        targetId = clubId.toString(),
                        details = """{"removedUserId":"$targetUserId"}"""
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "removed"))
                }
            }

            // GET /admin/users?q=&page=1&pageSize=50 — User search
            get("/users") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val query = call.parameters["q"] ?: ""
                    val page = call.parameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50
                    val users = adminRepository.searchUsers(query, page, pageSize)
                    call.respond(users)
                }
            }

            // GET /admin/users/{userId} — User detail
            get("/users/{userId}") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val userId = UUID.fromString(call.parameters["userId"])
                    val detail = adminRepository.getUserDetail(userId)
                    if (detail == null) {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    } else {
                        call.respond(detail)
                    }
                }
            }

            // GET /admin/audit-log?action=&actor=&startDate=&endDate=&page=1&pageSize=50
            get("/audit-log") {
                call.requireSuperAdmin(userRepository) { _ ->
                    val action = call.parameters["action"]
                    val actor = call.parameters["actor"]
                    val startDate = call.parameters["startDate"]?.let { Instant.parse(it) }
                    val endDate = call.parameters["endDate"]?.let { Instant.parse(it) }
                    val page = call.parameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50
                    val log = auditLogRepository.query(action, actor, startDate, endDate, page, pageSize)
                    call.respond(log)
                }
            }
        }
    }
}
