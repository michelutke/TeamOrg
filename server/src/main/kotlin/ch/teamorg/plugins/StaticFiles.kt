package ch.teamorg.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Serves user uploads (avatars, club logos).
 *
 * These bytes come from users, so they are served defensively: never sniffed, and never
 * allowed to run script. Together with the magic-byte validation applied at upload time
 * (`AuthRoutes` avatars, `ClubRoutes` logos), an attacker cannot turn an "image" into
 * stored XSS against `server.teamorg.ch`.
 */
fun Application.configureStaticFiles() {
    routing {
        route("/uploads") {
            install(createRouteScopedPlugin("UploadResponseHardening") {
                onCallRespond { call, _ ->
                    call.response.headers.append(
                        "Content-Security-Policy",
                        "default-src 'none'; img-src 'self'; sandbox",
                        safeOnly = false
                    )
                    // nosniff comes from the global header interceptor; only the stricter
                    // upload-specific policy is added here.
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=3600", safeOnly = false)
                }
            })
            staticFiles("/", File(System.getenv("UPLOADS_DIR") ?: "uploads"))
        }
    }
}
