package ch.teamorg

import ch.teamorg.infra.DatabaseFactory
import ch.teamorg.infra.startMaterialisationJob
import ch.teamorg.infra.startReminderSchedulerJob
import ch.teamorg.infra.startSwissVolleySyncJob
import ch.teamorg.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private val logger = LoggerFactory.getLogger("Application")

fun Application.module() {
    DatabaseFactory.init(environment.config)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respondText(
                text = "${cause::class.simpleName}: ${cause.message}\n${cause.stackTraceToString().take(2000)}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    configureKoin()
    configureSerialization()
    configureStaticFiles()
    configureAuth()
    configureRouting()
    startMaterialisationJob()
    startReminderSchedulerJob()
    startSwissVolleySyncJob()
}
