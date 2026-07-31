package ch.teamorg.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Rate-limit buckets. Applied per route group via `rateLimit(RateLimitName(...))`. */
object RateLimits {
    /** Credential endpoints: login, register, change-password. Brute-force defence. */
    val AUTH = RateLimitName("auth")

    /** Invite-code resolution: stops enumeration of the 8-char short-code space. */
    val INVITE_CODE = RateLimitName("invite-code")

    /** Public contact form: stops mail flooding through the landing site. */
    val CONTACT = RateLimitName("contact")
}

/**
 * The placeholder secret shipped in `application.conf` for local development.
 * Booting production with this value would let anyone forge tokens for any user,
 * so startup fails instead.
 */
private const val DEV_JWT_SECRET = "dev-secret-change-in-production"

private const val MIN_JWT_SECRET_LENGTH = 32

/**
 * Refuses to start with a guessable JWT signing key. A short or default HMAC secret is
 * equivalent to no authentication at all — every token in the system is forgeable.
 *
 * Set `ALLOW_WEAK_JWT_SECRET=true` only for local development.
 */
fun Application.verifyJwtSecretStrength() {
    val secret = environment.config.property("jwt.secret").getString()
    val allowWeak = System.getenv("ALLOW_WEAK_JWT_SECRET")?.equals("true", ignoreCase = true) == true
    if (allowWeak) {
        log.warn("ALLOW_WEAK_JWT_SECRET is set — never use this outside local development.")
        return
    }
    require(secret != DEV_JWT_SECRET) {
        "JWT_SECRET is still the development placeholder. Set a strong random JWT_SECRET " +
            "(>= $MIN_JWT_SECRET_LENGTH chars) before starting the server."
    }
    require(secret.length >= MIN_JWT_SECRET_LENGTH) {
        "JWT_SECRET must be at least $MIN_JWT_SECRET_LENGTH characters (got ${secret.length})."
    }
}

/**
 * Trusts the reverse proxy's `X-Forwarded-*` headers so rate limiting and logging see the
 * real client IP rather than Traefik's. Only meaningful because the container is never
 * exposed directly — Traefik is the sole ingress.
 */
fun Application.configureForwardedHeaders() {
    install(XForwardedHeaders)
}

/**
 * Response headers applied to every route.
 *
 * - `X-Content-Type-Options` stops MIME sniffing, which is what turns an uploaded image
 *   into stored XSS when a browser decides the bytes look like HTML.
 * - `X-Frame-Options` / `frame-ancestors` block clickjacking.
 * - `Referrer-Policy` keeps ids in paths out of third-party referer logs.
 * - `Strict-Transport-Security` pins clients to HTTPS after the first visit.
 * - The API serves JSON and user uploads only, so a restrictive CSP costs nothing here.
 */
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        val headers = call.response.headers
        headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
        headers.append("X-Frame-Options", "DENY", safeOnly = false)
        headers.append("Referrer-Policy", "strict-origin-when-cross-origin", safeOnly = false)
        headers.append("Cross-Origin-Resource-Policy", "same-site", safeOnly = false)
        headers.append("Permissions-Policy", "geolocation=(), microphone=(), camera=()", safeOnly = false)
        headers.append(
            "Content-Security-Policy",
            "default-src 'none'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
            safeOnly = false
        )
        if (call.request.origin.scheme == "https" || call.request.headers["X-Forwarded-Proto"] == "https") {
            headers.append(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains",
                safeOnly = false
            )
        }
    }
}

/**
 * Rate limiting, keyed by client IP (behind Traefik, via `X-Forwarded-For`).
 *
 * The auth bucket is deliberately tight: password guessing is the single most likely
 * attack on this API, and no legitimate client logs in ten times a minute.
 */
fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(RateLimits.AUTH) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.clientKey() }
        }
        register(RateLimits.INVITE_CODE) {
            rateLimiter(limit = 20, refillPeriod = 60.seconds)
            requestKey { call -> call.clientKey() }
        }
        register(RateLimits.CONTACT) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
            requestKey { call -> call.clientKey() }
        }
    }
}

/** Client identity for rate limiting: the proxy-resolved remote host. */
private fun ApplicationCall.clientKey(): String = request.origin.remoteHost
