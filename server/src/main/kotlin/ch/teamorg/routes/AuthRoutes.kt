package ch.teamorg.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ch.teamorg.domain.repositories.DeleteAccountOutcome
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserDeletionRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.middleware.authenticateUser
import ch.teamorg.plugins.RateLimits
import ch.teamorg.storage.FileStorageService
import ch.teamorg.storage.FileType
import ch.teamorg.storage.ImageValidation
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
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

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class DeleteAccountConflict(val reason: String, val clubs: List<String>)

fun Route.authRoutes() {
    val userRepository by inject<UserRepository>()
    val teamRepository by inject<TeamRepository>()
    val userDeletionRepository by inject<UserDeletionRepository>()
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
      // Only the endpoints that verify or set a credential are rate limited. /auth/me and
      // /auth/me/roles are called server-to-server by the admin app on every page load,
      // from a single container IP — limiting those would throttle all users at once.
      rateLimit(RateLimits.AUTH) {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            if (!isValidEmail(request.email)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid email format")
            }
            val passwordError = validatePassword(request.password)
            if (passwordError != null) {
                return@post call.respond(HttpStatusCode.BadRequest, passwordError)
            }
            if (request.displayName.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Display name cannot be empty")
            }
            if (request.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, "Display name is too long")
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

            if (passwordHash == null) {
                // Hash against a dummy value anyway: skipping bcrypt for unknown addresses
                // makes "no such user" measurably faster than "wrong password", which turns
                // the login endpoint into an account-enumeration oracle.
                BCrypt.checkpw(request.password.take(MAX_PASSWORD_LENGTH), DUMMY_BCRYPT_HASH)
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid email or password")
            }
            if (!BCrypt.checkpw(request.password.take(MAX_PASSWORD_LENGTH), passwordHash)) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid email or password")
            }

            val user = userRepository.findByEmail(request.email)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid email or password")
            val token = generateToken(user.id)
            call.respond(AuthResponse(token, user.id, user.displayName, user.avatarUrl))
        }
      }

        authenticate("jwt") {
            post("/logout") {
                // Stateless JWT logout - 200 OK
                call.respond(HttpStatusCode.OK)
            }

            rateLimit(RateLimits.AUTH) {
                post("/change-password") {
                    val request = call.receive<ChangePasswordRequest>()
                    val passwordError = validatePassword(request.newPassword)
                    if (passwordError != null) {
                        return@post call.respond(HttpStatusCode.BadRequest, passwordError)
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

                delete("/me") {
                    val request = call.receive<DeleteAccountRequest>()
                    call.authenticateUser(userRepository) { user ->
                        val userId = UUID.fromString(user.id)
                        val currentHash = userRepository.getPasswordHashById(userId)
                        if (currentHash == null ||
                            !BCrypt.checkpw(request.password.take(MAX_PASSWORD_LENGTH), currentHash)
                        ) {
                            return@authenticateUser call.respond(
                                HttpStatusCode.Unauthorized,
                                "Password is incorrect"
                            )
                        }
                        // Read before the scrub nulls the column.
                        val avatar = userDeletionRepository.avatarPath(userId)
                        when (val outcome = userDeletionRepository.deleteAccount(userId)) {
                            is DeleteAccountOutcome.OwnsClubs -> call.respond(
                                HttpStatusCode.Conflict,
                                DeleteAccountConflict("owns_clubs", outcome.clubNames)
                            )
                            DeleteAccountOutcome.Deleted -> {
                                // Outside the transaction on purpose: an orphaned file is a
                                // lesser harm than a rolled-back deletion.
                                if (avatar != null) {
                                    runCatching { fileStorageService.delete(avatar.removePrefix("/uploads/")) }
                                        .onFailure { call.application.log.warn("avatar cleanup failed for $userId", it) }
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                    }
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
                    var tooLarge = false

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem && fileBytes == null && !tooLarge) {
                            // Read at most the limit plus one byte: an oversized upload is
                            // rejected without ever buffering the whole body in memory.
                            val bytes = part.provider().readRemaining((ImageValidation.MAX_BYTES + 1).toLong()).readByteArray()
                            if (bytes.size > ImageValidation.MAX_BYTES) tooLarge = true else fileBytes = bytes
                        }
                        part.dispose()
                    }

                    if (tooLarge) {
                        return@authenticateUser call.respond(HttpStatusCode.PayloadTooLarge, "Avatar must be less than 2MB")
                    }
                    val bytes = fileBytes
                        ?: return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Avatar file required (jpg/png/webp)")

                    // The declared content type is attacker-controlled; the magic bytes are not.
                    val kind = ImageValidation.detect(bytes)
                        ?: return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Avatar must be a JPEG, PNG or WebP image")

                    val path = fileStorageService.save(bytes, FileType.AVATAR, kind.extension)
                    val updatedUser = userRepository.updateAvatarUrl(UUID.fromString(user.id), "/uploads/$path")
                    call.respond(updatedUser)
                }
            }
        }
    }
}

private const val MAX_DISPLAY_NAME_LENGTH = 100

/**
 * bcrypt silently ignores everything past 72 bytes, so an unbounded password field is
 * both a truncation surprise and a cheap CPU-burn vector (hashing is deliberately slow).
 */
private const val MAX_PASSWORD_LENGTH = 72

/** Hash of a value no user can supply; used to keep failed logins constant-cost. */
private val DUMMY_BCRYPT_HASH: String = BCrypt.hashpw("dummy-password-for-timing", BCrypt.gensalt(12))

private fun validatePassword(password: String): String? = when {
    password.length < 8 -> "Password must be at least 8 characters"
    password.length > MAX_PASSWORD_LENGTH -> "Password must be at most $MAX_PASSWORD_LENGTH characters"
    else -> null
}

private fun isValidEmail(email: String): Boolean {
    if (email.length > 254) return false
    return email.contains("@") && email.indexOf("@") < email.lastIndexOf(".")
}
