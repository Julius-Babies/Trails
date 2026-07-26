package es.jvbabi.trails.routes

import es.jvbabi.trails.routes.active_share.item.activeShareItem
import es.jvbabi.trails.routes.app.app
import es.jvbabi.trails.routes.app.session_healthcheck.sessionHealthCheck
import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappLogout
import es.jvbabi.trails.routes.devices.devices
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.devices.item.deviceItem
import es.jvbabi.trails.routes.me.emitted_shares.getEmittedShares
import es.jvbabi.trails.routes.me.me
import es.jvbabi.trails.routes.me.shares.getUserShares
import es.jvbabi.trails.routes.me.shares.registerUserShare
import es.jvbabi.trails.routes.share.createShare
import es.jvbabi.trails.routes.share.item.shareItem
import es.jvbabi.trails.routes.share.item.redeem.redeemShare
import es.jvbabi.trails.routes.user.item.userItem
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

                route("/shares") {
                    getUserShares()

                    route("/register") {
                        registerUserShare()
                    }
                }

                route("/emitted-shares") {
                    getEmittedShares()
                }
            }

            route("/devices") {
                devices()

                route("/{deviceId}") {
                    deviceItem()
                }
            }

            route("/share") {
                createShare()

                route("/{shareId}") {
                    shareItem()

                    route("/redeem") {
                        redeemShare()
                    }
                }
            }

            route("/active-shares") {
                route("/{activeShareId}") {
                    activeShareItem()
                }
            }

            route("/users") {
                route("/{userId}") {
                    userItem()
                }
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
