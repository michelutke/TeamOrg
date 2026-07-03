package ch.teamorg.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            // Defaulted DTO fields (e.g. Event.checkInStatus = "open") must still reach
            // non-Kotlin consumers: the web admin reads raw JSON and treated a missing
            // checkInStatus as "not open", locking RSVP on every open event.
            encodeDefaults = true
        })
    }
}
