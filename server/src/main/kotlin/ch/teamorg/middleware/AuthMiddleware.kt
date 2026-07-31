package ch.teamorg.middleware

import ch.teamorg.db.tables.ImpersonationSessionsTable
import ch.teamorg.domain.models.User
import ch.teamorg.domain.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.*

class UserPrincipal(val userId: UUID, val isSuperAdmin: Boolean) : Principal

suspend fun ApplicationCall.authenticateUser(
    userRepository: UserRepository,
    body: suspend (User) -> Unit
) {
    val principal = principal<JWTPrincipal>()
    val userIdString = principal?.payload?.subject

    if (userIdString == null) {
        respond(HttpStatusCode.Unauthorized, "Invalid token payload")
        return
    }

    val userId = try {
        UUID.fromString(userIdString)
    } catch (e: Exception) {
        respond(HttpStatusCode.Unauthorized, "Invalid user ID format")
        return
    }

    // An impersonation token must be backed by a live session row. Without this check,
    // "end impersonation" only flips a database flag while the issued token keeps working
    // for its full hour — and a leaked impersonation token could never be revoked at all.
    val sessionId = principal.payload.getClaim(IMPERSONATION_SESSION_CLAIM)?.asString()
    if (sessionId != null && !isImpersonationSessionLive(sessionId)) {
        respond(HttpStatusCode.Unauthorized, "Impersonation session is no longer active")
        return
    }

    val user = userRepository.findById(userId)
    if (user == null) {
        respond(HttpStatusCode.Unauthorized, "User not found")
        return
    }

    body(user)
}

const val IMPERSONATION_SESSION_CLAIM = "impersonation_session_id"

private suspend fun isImpersonationSessionLive(sessionId: String): Boolean {
    val id = try {
        UUID.fromString(sessionId)
    } catch (e: IllegalArgumentException) {
        return false
    }
    return withContext(Dispatchers.IO) {
        transaction {
            ImpersonationSessionsTable
                .selectAll()
                .where { ImpersonationSessionsTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    row[ImpersonationSessionsTable.isActive] &&
                        row[ImpersonationSessionsTable.expiresAt].isAfter(Instant.now())
                } ?: false
        }
    }
}
