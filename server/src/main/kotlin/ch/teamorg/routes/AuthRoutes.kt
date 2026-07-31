package ch.teamorg.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.middleware.authenticateUser
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
import org.mindrot.jbcrypt.BCrypt
import org.koin.ktor.ext.inject
import java.util.*

@Serializable
data class UserRolesResponse(val clubRoles: List<ClubRoleEntry>, val teamRoles: List<TeamRoleEntry>)

@Serializable
data class ClubRoleEntry(val clubId: String, val role: String)

@Serializable
data class TeamRoleEntry(val teamId: String, val clubId: String, val role: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val userId: String, val displayName: String, val avatarUrl: String?)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

fun Route.authRoutes() {
    val userRepository by inject<UserRepository>()
    val teamRepository by inject<TeamRepository>()
    val fileStorageService by inject<FileStorageService>()

    val jwtSecret = application.environment.config.property("jwt.secret").getString()
    val jwtIssuer = application.environment.config.property("jwt.issuer").getString()
    val jwtAudience = application.environment.config.property("jwt.audience").getString()
    val expiryDays = application.environment.config.property("jwt.expiry-days").getString().toLong()

    fun generateToken(userId: String): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + expiryDays * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            if (!isValidEmail(request.email)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid email format")
            }
            if (request.password.length < 8) {
                return@post call.respond(HttpStatusCode.BadRequest, "Password must be at least 8 characters")
            }
            if (request.displayName.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Display name cannot be empty")
            }

            if (userRepository.existsByEmail(request.email)) {
                return@post call.respond(HttpStatusCode.Conflict, "Email already registered")
            }

            val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt(12))
            val user = userRepository.create(request.email, passwordHash, request.displayName)

            val token = generateToken(user.id)
            call.respond(AuthResponse(token, user.id, user.displayName, user.avatarUrl))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val passwordHash = userRepository.getPasswordHash(request.email)

            if (passwordHash == null || !BCrypt.checkpw(request.password, passwordHash)) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid email or password")
            }

            val user = userRepository.findByEmail(request.email)!!
            val token = generateToken(user.id)
            call.respond(AuthResponse(token, user.id, user.displayName, user.avatarUrl))
        }

        authenticate("jwt") {
            post("/logout") {
                // Stateless JWT logout - 200 OK
                call.respond(HttpStatusCode.OK)
            }

            post("/change-password") {
                val request = call.receive<ChangePasswordRequest>()
                if (request.newPassword.length < 8) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Password must be at least 8 characters")
                }
                call.authenticateUser(userRepository) { user ->
                    val userId = UUID.fromString(user.id)
                    val currentHash = userRepository.getPasswordHashById(userId)
                    if (currentHash == null || !BCrypt.checkpw(request.currentPassword, currentHash)) {
                        return@authenticateUser call.respond(HttpStatusCode.Unauthorized, "Current password is incorrect")
                    }
                    val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt(12))
                    userRepository.updatePasswordHash(userId, newHash)
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/me") {
                call.authenticateUser(userRepository) { user ->
                    call.respond(user)
                }
            }

            get("/me/roles") {
                call.authenticateUser(userRepository) { user ->
                    val userId = UUID.fromString(user.id)
                    val clubRoles = teamRepository.getUserClubRoles(userId)
                    val teamRoles = teamRepository.getUserTeamRoles(userId)
                    call.respond(UserRolesResponse(
                        clubRoles = clubRoles.map { ClubRoleEntry(it.first.toString(), it.second) },
                        teamRoles = teamRoles.map { TeamRoleEntry(it.first.toString(), it.second.toString(), it.third) }
                    ))
                }
            }

            post("/me/avatar") {
                call.authenticateUser(userRepository) { user ->
                    val multipart = call.receiveMultipart()
                    var fileBytes: ByteArray? = null
                    var extension: String? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val contentType = part.contentType
                            if (contentType != null && listOf("image/jpeg", "image/jpg", "image/png", "image/webp").contains(contentType.toString())) {
                                extension = when (contentType.toString()) {
                                    "image/jpeg", "image/jpg" -> "jpg"
                                    "image/png" -> "png"
                                    "image/webp" -> "webp"
                                    else -> "bin"
                                }
                                fileBytes = part.streamProvider().readBytes()
                            }
                        }
                        part.dispose()
                    }

                    if (fileBytes == null) {
                        return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Avatar file required (jpg/png/webp)")
                    }
                    if (fileBytes!!.size > 2 * 1024 * 1024) {
                        return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Avatar must be less than 2MB")
                    }

                    val path = fileStorageService.save(fileBytes!!, FileType.AVATAR, extension!!)
                    val updatedUser = userRepository.updateAvatarUrl(UUID.fromString(user.id), "/uploads/$path")
                    call.respond(updatedUser)
                }
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    return email.contains("@") && email.indexOf("@") < email.lastIndexOf(".")
}
