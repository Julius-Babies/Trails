package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.devices.image.deviceImage
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.installRouting() {
    routing {
        route("/api/v1") {
            route("/auth/app-authorization") {
                appAuthorization()
            }

            route("/devices") {
                route("/image") {
                    deviceImage()
                }
            }
        }
    }
}