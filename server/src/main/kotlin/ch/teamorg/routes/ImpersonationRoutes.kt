package ch.teamorg.routes

import ch.teamorg.db.tables.ImpersonationSessionsTable
import ch.teamorg.domain.repositories.AuditLogRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.middleware.requireSuperAdmin
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.util.Date
import java.util.UUID

@Serializable
data class StartImpersonationRequest(val targetUserId: String)

@Serializable
data class ImpersonationTargetUser(val id: String, val email: String, val displayName: String)

@Serializable
data class ImpersonationResponse(
    val token: String,
    val sessionId: String,
    val targetUser: ImpersonationTargetUser,
    val expiresInSeconds: Int
)

@Serializable
data class ImpersonationStatusResponse(
    val impersonating: Boolean,
    val impersonatorId: String? = null,
    val sessionId: String? = null,
    val remainingSeconds: Long? = null
)

fun Route.impersonationRoutes() {
    val userRepository by inject<UserRepository>()
    val auditLogRepository by inject<AuditLogRepository>()

    val jwtSecret = application.environment.config.property("jwt.secret").getString()
    val jwtIssuer = application.environment.config.property("jwt.issuer").getString()
    val jwtAudience = application.environment.config.property("jwt.audience").getString()

    authenticate("jwt") {
        route("/admin/impersonate") {
            // POST /admin/impersonate/start — Start impersonation session (SA-08)
            post("/start") {
                call.requireSuperAdmin(userRepository) { actor ->
                    val request = call.receive<StartImpersonationRequest>()
                    val targetId = UUID.fromString(request.targetUserId)
                    val target = userRepository.findById(targetId)

                    if (target == null) {
                        return@requireSuperAdmin call.respond(HttpStatusCode.NotFound, "Target user not found")
                    }

                    if (target.isSuperAdmin) {
                        return@requireSuperAdmin call.respond(
                            HttpStatusCode.BadRequest,
                            "Cannot impersonate another SuperAdmin"
                        )
                    }

                    val expiresAt = java.time.Instant.now().plusSeconds(3600)

                    val sessionId = transaction {
                        ImpersonationSessionsTable.insert {
                            it[ImpersonationSessionsTable.actorId] = UUID.fromString(actor.id)
                            it[ImpersonationSessionsTable.targetId] = targetId
                            it[ImpersonationSessionsTable.expiresAt] = expiresAt
                            it[ImpersonationSessionsTable.isActive] = true
                        } get ImpersonationSessionsTable.id
                    }

                    val token = JWT.create()
                        .withAudience(jwtAudience)
                        .withIssuer(jwtIssuer)
                        .withSubject(targetId.toString())
                        .withClaim("impersonator_id", actor.id)
                        .withClaim("impersonation_session_id", sessionId.toString())
                        .withExpiresAt(Date(System.currentTimeMillis() + 3600 * 1000L))
                        .sign(Algorithm.HMAC256(jwtSecret))

                    auditLogRepository.log(
                        actorId = UUID.fromString(actor.id),
                        actorEmail = actor.email,
                        action = "impersonation.start",
                        targetType = "user",
                        targetId = targetId.toString(),
                        details = """{"targetEmail":"${target.email}","sessionId":"$sessionId"}"""
                    )

                    call.respond(
                        ImpersonationResponse(
                            token = token,
                            sessionId = sessionId.toString(),
                            targetUser = ImpersonationTargetUser(target.id, target.email, target.displayName),
                            expiresInSeconds = 3600
                        )
                    )
                }
            }

            // POST /admin/impersonate/end — End impersonation session
            post("/end") {
                val principal = call.principal<JWTPrincipal>()
                val impersonatorId = principal?.payload?.getClaim("impersonator_id")?.asString()
                val sessionId = principal?.payload?.getClaim("impersonation_session_id")?.asString()

                if (impersonatorId == null || sessionId == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Not an impersonation session")
                }

                transaction {
                    ImpersonationSessionsTable.update(
                        { ImpersonationSessionsTable.id eq UUID.fromString(sessionId) }
                    ) {
                        it[ImpersonationSessionsTable.isActive] = false
                        it[ImpersonationSessionsTable.endedAt] = java.time.Instant.now()
                    }
                }

                val impersonator = userRepository.findById(UUID.fromString(impersonatorId))
                if (impersonator != null) {
                    auditLogRepository.log(
                        actorId = UUID.fromString(impersonatorId),
                        actorEmail = impersonator.email,
                        action = "impersonation.end",
                        targetType = "session",
                        targetId = sessionId
                    )
                }

                call.respond(HttpStatusCode.OK, mapOf("status" to "ended"))
            }

            // GET /admin/impersonate/status — Check if current token is impersonation
            get("/status") {
                val principal = call.principal<JWTPrincipal>()
                val impersonatorId = principal?.payload?.getClaim("impersonator_id")?.asString()
                val sessionId = principal?.payload?.getClaim("impersonation_session_id")?.asString()

                if (impersonatorId == null || sessionId == null) {
                    call.respond(ImpersonationStatusResponse(impersonating = false))
                } else {
                    val sessionRow = transaction {
                        ImpersonationSessionsTable.selectAll()
                            .where { ImpersonationSessionsTable.id eq UUID.fromString(sessionId) }
                            .singleOrNull()
                    }
                    val expiresAt = sessionRow?.get(ImpersonationSessionsTable.expiresAt)
                    val remainingSeconds = if (expiresAt != null) {
                        java.time.Duration.between(java.time.Instant.now(), expiresAt).seconds.coerceAtLeast(0)
                    } else {
                        0L
                    }

                    call.respond(
                        ImpersonationStatusResponse(
                            impersonating = true,
                            impersonatorId = impersonatorId,
                            sessionId = sessionId,
                            remainingSeconds = remainingSeconds
                        )
                    )
                }
            }
        }
    }
}
