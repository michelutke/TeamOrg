package ch.teamorg.crash

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class CrashReportingTest {

    @Test
    fun `init is skipped for an empty dsn`() {
        assertFalse(initCrashReporting(dsn = "", environment = "production", release = "ch.teamorg@1.0+1"))
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `init is skipped for a blank dsn`() {
        assertFalse(initCrashReporting(dsn = "  ", environment = "production", release = "ch.teamorg@1.0+1"))
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `user calls are no-ops while sentry is not initialized`() {
        CrashReporting.setUser("user-1")
        CrashReporting.clearUser()
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `privacy options disable pii, attachments and tracing`() {
        val options = SentryOptions()
        applyPrivacyOptions(options)

        assertFalse(options.sendDefaultPii)
        assertFalse(options.attachScreenshot)
        assertFalse(options.attachViewHierarchy)
        assertFalse(options.enableCaptureFailedRequests)
        assertEquals(0.0, options.tracesSampleRate)
    }

    @Test
    fun `event user is reduced to the id`() {
        val event = SentryEvent()
        event.user = User(email = "a@b.ch", id = "user-1", username = "anna", ipAddress = "1.2.3.4")

        val sanitized = sanitizeEvent(event)

        assertEquals("user-1", sanitized.user?.id)
        assertNull(sanitized.user?.email)
        assertNull(sanitized.user?.username)
        assertNull(sanitized.user?.ipAddress)
    }

    @Test
    fun `event without a user id has no user`() {
        val event = SentryEvent()
        event.user = User(email = "a@b.ch")

        assertNull(sanitizeEvent(event).user)
    }

    @Test
    fun `http breadcrumbs are dropped`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.type = "http"
        breadcrumb.setData("url", "https://server.teamorg.ch/invites/secret-token/redeem")

        assertNull(sanitizeBreadcrumb(breadcrumb))
    }

    @Test
    fun `non http breadcrumbs are kept`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.type = "navigation"

        assertSame(breadcrumb, sanitizeBreadcrumb(breadcrumb))
    }

    @Test
    fun `init with a malformed dsn returns false without throwing`() {
        assertFalse(initCrashReporting(dsn = "garbage", environment = "production", release = "ch.teamorg@1.0+1"))
        assertFalse(Sentry.isEnabled())
    }
}
