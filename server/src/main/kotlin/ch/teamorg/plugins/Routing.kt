package ch.teamorg.plugins

import ch.teamorg.routes.abwesenheitRoutes
import ch.teamorg.routes.adminRoutes
import ch.teamorg.routes.attendanceRoutes
import ch.teamorg.routes.authRoutes
import ch.teamorg.routes.checkInRoutes
import ch.teamorg.routes.clubRoutes
import ch.teamorg.routes.eventRoutes
import ch.teamorg.routes.impersonationRoutes
import ch.teamorg.routes.inviteRoutes
import ch.teamorg.routes.notificationRoutes
import ch.teamorg.routes.subGroupRoutes
import ch.teamorg.routes.teamRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, "OK")
        }
        authRoutes()
        clubRoutes()
        teamRoutes()
        inviteRoutes()
        eventRoutes()
        subGroupRoutes()
        attendanceRoutes()
        abwesenheitRoutes()
        checkInRoutes()
        notificationRoutes()
        adminRoutes()
        impersonationRoutes()
    }
}
