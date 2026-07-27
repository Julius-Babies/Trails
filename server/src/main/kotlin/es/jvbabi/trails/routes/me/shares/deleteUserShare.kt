package es.jvbabi.trails.routes.me.shares

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.UserShare
import es.jvbabi.trails.database.UserShares
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * `DELETE /me/shares/{shareId}?homeserver=<host>` — removes a saved share from
 * the caller's account (the account-side half of "returning" a share). The
 * share is identified by its active-share id plus origin homeserver, matching
 * how it was registered. Reachable by both the app and the web realm.
 *
 * This only drops the account's backup reference; deleting the underlying
 * [es.jvbabi.trails.database.ActiveShare] on the origin homeserver — and thus
 * *not* lifting the share's lock — is the client's separate, direct call to
 * that origin. On success the webapp socket is nudged via
 * [UserSubscriptionMessage.SharesChanged] so the share drops from the list live.
 */
fun Route.deleteUserShare() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        delete {
            val appPrincipal = call.principal<TrailsAppUserPrincipal>()
            appPrincipal?.requireValidSession()
            val user = appPrincipal?.user
                ?: call.principal<TrailsWebappPrincipal>()?.user
                ?: return@delete call.respond(HttpStatusCode.Forbidden)

            val shareId = call.parameters["shareId"]?.let(Uuid::parseOrNull)
                ?: return@delete call.respond(HttpStatusCode.NotFound)
            // Absent homeserver means a same-server share, stored as "".
            val homeserver = call.request.queryParameters["homeserver"] ?: ""

            val removed = db.transaction {
                val matches = UserShare.find {
                    (UserShares.user eq user.id) and
                            (UserShares.shareId eq shareId) and
                            (UserShares.homeserver eq homeserver)
                }.toList()
                matches.forEach { it.delete() }
                matches.isNotEmpty()
            }

            if (removed) {
                userSubscriptionRepository.getFlowForUser(user.id.value)
                    .emit(UserSubscriptionMessage.SharesChanged(shareId))
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
