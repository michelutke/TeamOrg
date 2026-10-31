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
                // The same lookup enforces password-reset revocation: a reset bumps the user's
                // token version, so tokens minted before it no longer match. Impersonation
                // tokens are minted by an admin for a target and are revoked via their session.
                val tokenVersion = credential.payload.getClaim(TOKEN_VERSION_CLAIM).asInt() ?: 0
                val isImpersonation = !credential.payload.getClaim("impersonator_id").isMissing
                if (!isSessionValid(userId, tokenVersion, isImpersonation)) return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

const val TOKEN_VERSION_CLAIM = "tv"

private fun isSessionValid(userId: UUID, tokenVersion: Int, isImpersonation: Boolean): Boolean = transaction {
    val row = UsersTable.select(UsersTable.deletedAt, UsersTable.tokenVersion)
        .where { UsersTable.id eq userId }
        .singleOrNull()
        ?: return@transaction true
    row[UsersTable.deletedAt] == null && (isImpersonation || row[UsersTable.tokenVersion] == tokenVersion)
}
