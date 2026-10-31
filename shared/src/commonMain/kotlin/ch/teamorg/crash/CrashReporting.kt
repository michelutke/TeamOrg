package ch.teamorg.crash

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

interface CrashReportingBridge {
    fun setUser(userId: String)
    fun clearUser()
}

object CrashReporting : CrashReportingBridge {
    override fun setUser(userId: String) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.user = User(id = userId) }
    }

    override fun clearUser() {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.user = null }
    }
}

/**
 * Starts Sentry. A blank [dsn] (debug, local and test builds) leaves Sentry completely off.
 * Returns whether Sentry was initialized; a malformed DSN is logged and leaves Sentry off.
 */
fun initCrashReporting(dsn: String, environment: String, release: String): Boolean {
    if (dsn.isBlank()) return false
    return runCatching {
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = environment
            options.release = release
            applyPrivacyOptions(options)
        }
    }.onFailure { println("Crash reporting disabled, Sentry init failed: ${it.message}") }.isSuccess
}

internal fun applyPrivacyOptions(options: SentryOptions) {
    options.sendDefaultPii = false
    options.attachScreenshot = false
    options.attachViewHierarchy = false
    options.enableCaptureFailedRequests = false
    options.enableUnhandledCppExceptionMonitoring = false
    options.tracesSampleRate = 0.0
    options.beforeSend = ::sanitizeEvent
    options.beforeBreadcrumb = ::sanitizeBreadcrumb
}

internal fun sanitizeEvent(event: SentryEvent): SentryEvent {
    event.user = event.user?.id?.let { User(id = it) }
    return event
}

// HTTP breadcrumbs carry request paths, which include invite tokens and codes.
internal fun sanitizeBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb? =
    if (breadcrumb.type == "http") null else breadcrumb
