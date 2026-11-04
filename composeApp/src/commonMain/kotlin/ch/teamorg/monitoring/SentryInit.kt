package ch.teamorg.monitoring

import io.sentry.kotlin.multiplatform.Sentry

private const val TRACES_SAMPLE_RATE = 0.2

fun initSentry(dsn: String, environment: String, release: String?) {
    if (dsn.isBlank()) return
    Sentry.init { options ->
        options.dsn = dsn
        options.environment = environment
        options.release = release
        options.sendDefaultPii = false
        options.tracesSampleRate = TRACES_SAMPLE_RATE
        options.attachScreenshot = false
        options.attachViewHierarchy = false
        options.sessionReplay.sessionSampleRate = 0.0
        options.sessionReplay.onErrorSampleRate = 0.0
        options.enableUnhandledCppExceptionMonitoring = false
    }
}
