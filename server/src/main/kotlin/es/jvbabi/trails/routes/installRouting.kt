package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.app.share.newShare
import es.jvbabi.trails.routes.app.share.useShare
import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.me.devices
import es.jvbabi.trails.routes.me.me
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.*
import routes.app.app
import java.io.File

fun Application.installRouting(
    webStaticRoot: String?,
) {
    routing {
        if (webStaticRoot != null) {
            staticFiles("/", File(webStaticRoot))
        }
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

                route("/share") {
                    newShare()

                    route("/use") {
                        useShare()
                    }
                }
            }

            route("/devices") {
                route("/image") {
                    deviceImage()
                }
            }
        }
    }
}