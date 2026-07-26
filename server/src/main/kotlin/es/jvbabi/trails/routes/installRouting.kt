package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.app.app
import es.jvbabi.trails.routes.app.session_healthcheck.sessionHealthCheck
import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappLogout
import es.jvbabi.trails.routes.devices.devices
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.entity.getActiveShareById
import es.jvbabi.trails.routes.entity.getDeviceById
import es.jvbabi.trails.routes.entity.getShareById
import es.jvbabi.trails.routes.entity.getUserById
import es.jvbabi.trails.routes.share.createShare
import es.jvbabi.trails.routes.me.me
import es.jvbabi.trails.routes.share.item.redeem.redeemShare
import es.jvbabi.trails.routes.webapp.mapbox.webappMapbox
import es.jvbabi.trails.routes.webapp.me.webappMe
import es.jvbabi.trails.routes.webapp.webappSocket
import io.ktor.server.application.*
import io.ktor.server.routing.*

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
                getDeviceById()
            }

            route("/share") {
                createShare()
                getShareById()

                route("/{shareId}") {
                    route("/redeem") {
                        redeemShare()
                    }
                }
            }

            route("/active-shares") {
                getActiveShareById()
            }

            route("/users") {
                getUserById()
            }

            route("/app") {
                app()

                route("/session-healthcheck") {
                    sessionHealthCheck()
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