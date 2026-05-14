package es.jvbabi.trails.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.installRouting() {
    routing {
        route("/api/v1") {

        }

        route("/auth/app-authorization") {
            get {
                call.respond("Anmelden in der Trails App...")
            }
        }
    }
}