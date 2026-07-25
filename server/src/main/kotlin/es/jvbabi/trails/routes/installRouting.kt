package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.app.session_healthcheck.sessionHealthCheck
import es.jvbabi.trails.routes.app.share.newShare
import es.jvbabi.trails.routes.app.share.useShare
import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.devices.devices
import es.jvbabi.trails.routes.me.me
import io.ktor.server.application.*
import io.ktor.server.routing.*
import es.jvbabi.trails.routes.app.app
import es.jvbabi.trails.routes.auth.webapp.webappAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappLogout
import es.jvbabi.trails.routes.webapp.me.webappMe
import es.jvbabi.trails.routes.webapp.mapbox.webappMapbox
import es.jvbabi.trails.routes.webapp.webappSocket

fun Application.installRouting() {
    routing {
        route("/api/v1") {
            route("/auth") {
                route("/app-authorization") {
                    appAuthorization()
                }

                route("/webapp-authorization") {
                    webappAuthorization()
                }
            }

            route("/me") {
                me()
            }

            route("/devices") {
                devices()
            }

            route("/app") {
                app()

                route("/session-healthcheck") {
                    sessionHealthCheck()
                }

                route("/share") {
                    newShare()

                    route("/use") {
                        useShare()
                    }
                }
            }

            route("/webapp") {

                route("/ws") {
                    webappSocket()
                }

                route("/me") {
                    webappMe()
                }

                route("/mapbox") {
                    webappMapbox()
                }

                route("/auth") {
                    route("/logout") {
                        webappLogout()
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