package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.devices.image.deviceImage
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.positions.positions

fun Application.installRouting() {
    routing {
        route("/api/v1") {
            route("/auth/app-authorization") {
                appAuthorization()
            }

            route("/positions") {
                positions()
            }

            route("/devices") {
                route("/image") {
                    deviceImage()
                }
            }
        }
    }
}