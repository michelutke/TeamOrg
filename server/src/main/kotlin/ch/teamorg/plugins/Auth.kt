package ch.teamorg.plugins

import ch.teamorg.db.tables.UsersTable
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Application.configureAuth() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val myRealm = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt") {
            realm = myRealm
            verifier(
                JWT.require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null
                val userId = try {
                    UUID.fromString(subject)
                } catch (e: IllegalArgumentException) {
                    return@validate null
                }
                // A self-deleted account must stop working immediately, not when its JWT
                // expires. This is the ONLY place every route passes through: eleven route
                // files read the principal directly and never call authenticateUser, so a
                // check placed there would leave them reachable with a deleted user's token.
                // Cost is one indexed primary-key lookup per authenticated request.
                if (isUserDeleted(userId)) return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

private fun isUserDeleted(userId: UUID): Boolean = transaction {
    UsersTable.select(UsersTable.deletedAt)
        .where { UsersTable.id eq userId }
        .singleOrNull()
        ?.get(UsersTable.deletedAt) != null
}
