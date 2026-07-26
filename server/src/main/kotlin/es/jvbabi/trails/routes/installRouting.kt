package es.jvbabi.trails.routes

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.mapper.toApi
import es.jvbabi.trails.routes.active_share.item.getActiveShare
import es.jvbabi.trails.routes.app.app
import es.jvbabi.trails.routes.app.session_healthcheck.sessionHealthCheck
import es.jvbabi.trails.routes.auth.app_authorization.appAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappAuthorization
import es.jvbabi.trails.routes.auth.webapp.webappLogout
import es.jvbabi.trails.routes.devices.devices
import es.jvbabi.trails.routes.devices.image.deviceImage
import es.jvbabi.trails.routes.devices.item.getDevice
import es.jvbabi.trails.routes.me.me
import es.jvbabi.trails.routes.me.shares.getUserShares
import es.jvbabi.trails.routes.me.shares.registerUserShare
import es.jvbabi.trails.routes.share.createShare
import es.jvbabi.trails.routes.share.item.getShare
import es.jvbabi.trails.routes.share.item.redeem.redeemShare
import es.jvbabi.trails.routes.user.item.getUser
import es.jvbabi.trails.routes.webapp.mapbox.webappMapbox
import es.jvbabi.trails.routes.webapp.me.webappMe
import es.jvbabi.trails.routes.webapp.webappSocket
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.installRouting() {
    val db by inject<DatabaseManager>()

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
            }

            route("/devices") {
                devices()

                route("/{deviceId}") {
                    get {
                        val device = call.getDevice()
                        call.respond(db.transaction { device.toApi() })
                    }
                }
            }

            route("/share") {
                createShare()

                route("/{shareId}") {
                    get {
                        val share = call.getShare()
                        call.respond(db.transaction { share.toApi() })
                    }

                    route("/redeem") {
                        redeemShare()
                    }
                }
            }

            route("/active-shares") {
                route("/{activeShareId}") {
                    get {
                        val activeShare = call.getActiveShare()
                        call.respond(db.transaction { activeShare.toApi() })
                    }
                }
            }

            route("/users") {
                route("/{userId}") {
                    get {
                        val user = call.getUser()
                        call.respond(db.transaction { user.toApi() })
                    }
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
