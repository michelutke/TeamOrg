package ch.teamorg

import ch.teamorg.infra.DatabaseFactory
import ch.teamorg.infra.startBillingJobs
import ch.teamorg.infra.startMaterialisationJob
import ch.teamorg.infra.startReminderSchedulerJob
import ch.teamorg.infra.startSwissVolleySyncJob
import ch.teamorg.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.UUID

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private val logger = LoggerFactory.getLogger("Application")

fun Application.module() {
    verifyJwtSecretStrength()
    DatabaseFactory.init(environment.config)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Correlation id lets support match a user's error to the server log without
            // ever putting exception type, message or stack trace on the wire — those leak
            // library versions, file paths, SQL fragments and occasionally secrets.
            val errorId = UUID.randomUUID().toString()
            logger.error("Unhandled exception [errorId=$errorId] ${call.request.local.method.value} ${call.request.path()}", cause)
            call.respondText(
                text = "Internal server error (ref: $errorId)",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    configureForwardedHeaders()
    configureSecurityHeaders()
    configureRateLimiting()
    configureKoin()
    configureSerialization()
    configureStaticFiles()
    configureAuth()
    configureRouting()
    startMaterialisationJob()
    startReminderSchedulerJob()
    startSwissVolleySyncJob()
    startBillingJobs()
}
