package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.me.me
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.me.devices
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.app.app

fun Application.installRouting() {
    routing {
        route("/api/v1") {
            route("/auth") {
                route("/app-authorization") {
                    appAuthorization()
                }

            }

            route("/me") {
                me()

                route("/devices") {
                    devices()
                }
            }

            route("/app") {
                app()
            }

            route("/devices") {
                route("/image") {
                    deviceImage()
                }
            }
        }
    }
}