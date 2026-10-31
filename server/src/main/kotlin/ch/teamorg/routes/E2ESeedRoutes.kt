package ch.teamorg.routes

import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
data class E2ESeedClubRequest(val name: String, val managerEmail: String, val sportType: String = "volleyball")

fun Application.isE2ESeedEnabled(): Boolean =
    environment.config.propertyOrNull("e2e.seed-enabled")?.getString()?.equals("true", ignoreCase = true) == true

/**
 * Test-only bootstrap: clubs normally need a super-admin or a Stripe card, neither of which
 * an end-to-end run has. Registered only when `E2E_SEED_ENABLED=true`; otherwise the path is a plain 404.
 */
fun Route.e2eSeedRoutes() {
    val clubRepository by inject<ClubRepository>()
    val userRepository by inject<UserRepository>()

    post("/e2e/seed/club") {
        val request = call.receive<E2ESeedClubRequest>()
        if (request.name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Club name is required")
        }
        val manager = userRepository.findByEmail(request.managerEmail)
            ?: return@post call.respond(HttpStatusCode.NotFound, "Manager not found")
        val club = clubRepository.create(
            name = request.name,
            sportType = request.sportType,
            location = null,
            creatorUserId = UUID.fromString(manager.id)
        )
        call.respond(HttpStatusCode.Created, club)
    }
}
