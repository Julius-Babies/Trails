package es.jvbabi.trails.routes.me.shares

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.v1.me.RegisterUserShareRequest
import es.jvbabi.trails.api.v1.me.UserShareResponse
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.UserShare
import es.jvbabi.trails.database.UserShares
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject

fun Route.getUserShares() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        get {
            val principal = call.principal<TrailsAppUserPrincipal>()!!
            principal.requireValidSession()

            val shares = db.transaction {
                UserShare.find { UserShares.user eq principal.user.id }
                    .map {
                        UserShareResponse(
                            shareId = it.shareId,
                            homeserver = it.homeserver,
                            createdAt = it.createdAt.epochSeconds,
                        )
                    }
            }

            call.respond(shares)
        }
    }
}

fun Route.registerUserShare() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        post {
            val principal = call.principal<TrailsAppUserPrincipal>()!!
            principal.requireValidSession()

            val request = call.receive<RegisterUserShareRequest>()

            db.transaction {
                val alreadyRegistered = !UserShare.find {
                    (UserShares.user eq principal.user.id) and
                            (UserShares.shareId eq request.shareId) and
                            (UserShares.homeserver eq request.homeserver)
                }.empty()

                if (!alreadyRegistered) {
                    UserShare.new {
                        this.user = principal.user
                        this.shareId = request.shareId
                        this.homeserver = request.homeserver
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
