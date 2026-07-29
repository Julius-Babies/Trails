package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.ActiveShares
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.UserShare
import es.jvbabi.trails.database.UserShares
import es.jvbabi.trails.routes.devices.item.deviceActor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * `DELETE /share/{shareId}` — deletes a share the caller emitted. Reachable by
 * both the app and the web realm.
 *
 * Unlike a device, a share is deleted for real: its redemptions
 * ([ActiveShare] rows) go with it via the database cascade, so every link handed
 * out stops working and nobody can see the device's location through it anymore.
 *
 * Saved references to those redemptions that live *on this server* are removed
 * too, and their owners are told via [UserSubscriptionMessage.SharesChanged] so
 * the share drops out of their lists. Savers on a foreign homeserver keep a
 * reference we cannot touch; their clients reconcile it against this server the
 * usual way (a snapshot socket that finds nothing, or `active-shares/bulk-check`).
 */
fun Route.deleteShare() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        delete {
            val actor = call.deviceActor(db)
                ?: return@delete call.respond(HttpStatusCode.Forbidden)
            val shareId = call.parameters["shareId"]?.let(Uuid::parseOrNull)
                ?: return@delete call.respond(HttpStatusCode.NotFound)

            // Resolve + ownership-check + delete in one transaction; null means the
            // share is missing or not the caller's, both answered as Forbidden so
            // the endpoint doesn't reveal which.
            val affectedSavers = db.transaction {
                val share = Share.findById(shareId) ?: return@transaction null
                if (share.device.owner.id.value != actor.userId) return@transaction null

                val activeShareIds = ActiveShare
                    .find { ActiveShares.share eq share.id }
                    .map { it.id.value }

                // Drop the local references to these redemptions, remembering whose
                // they were so those users can be notified once the share is gone.
                val savers = activeShareIds.flatMap { activeShareId ->
                    UserShare.find { UserShares.shareId eq activeShareId }.toList().map { userShare ->
                        val userId = userShare.user.id.value
                        userShare.delete()
                        userId to activeShareId
                    }
                }

                // Cascades to the share's active shares.
                share.delete()
                savers
            } ?: return@delete call.respond(HttpStatusCode.Forbidden)

            userSubscriptionRepository.getFlowForUser(actor.userId)
                .emit(UserSubscriptionMessage.EmittedSharesChanged(shareId))

            affectedSavers.forEach { (userId, activeShareId) ->
                userSubscriptionRepository.getFlowForUser(userId)
                    .emit(UserSubscriptionMessage.SharesChanged(activeShareId))
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
