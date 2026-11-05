package ch.teamorg.plugins

import io.ktor.server.application.*
import io.sentry.Sentry

/**
 * Initialises Sentry error monitoring from the environment.
 *
 * `SENTRY_DSN` unset or blank skips init entirely, so local development and tests send
 * nothing. ERROR logs with a throwable reach Sentry through the logback appender in
 * `logback.xml`, which relies on the SDK initialised here.
 */
fun Application.configureSentry() {
    val dsn = System.getenv("SENTRY_DSN")?.takeIf { it.isNotBlank() } ?: return
    val environmentName = System.getenv("SENTRY_ENVIRONMENT")?.takeIf { it.isNotBlank() } ?: "production"
    val releaseName = System.getenv("SENTRY_RELEASE")?.takeIf { it.isNotBlank() }
    Sentry.init { options ->
        options.dsn = dsn
        options.environment = environmentName
        options.release = releaseName
        options.isSendDefaultPii = false
    }
    log.info("Sentry initialised (environment=$environmentName)")
}
