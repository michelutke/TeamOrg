package ch.teamorg.middleware

import ch.teamorg.domain.models.User
import ch.teamorg.domain.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.requireSuperAdmin(
    userRepository: UserRepository,
    body: suspend (User) -> Unit
) {
    authenticateUser(userRepository) { user ->
        if (!user.isSuperAdmin) {
            respond(HttpStatusCode.Forbidden, "SuperAdmin access required")
            return@authenticateUser
        }
        body(user)
    }
}
