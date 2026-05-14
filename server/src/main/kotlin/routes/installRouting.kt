package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.installRouting() {
    routing {
        route("/api/v1") {
            route("/auth/app-authorization") {
                appAuthorization()
            }
        }
    }
}