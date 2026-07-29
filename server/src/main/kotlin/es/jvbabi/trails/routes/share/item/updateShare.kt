package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.v1.share.UpdateShareRequest
import es.jvbabi.trails.api.v1.share.UpdateShareResponse
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.Shares
import es.jvbabi.trails.ifDefined
import es.jvbabi.trails.routes.devices.item.deviceActor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * `PATCH /share/{shareId}` — lets the emitting user change a share's settings
 * (see [UpdateShareRequest]). Reachable by both the app and the web realm.
 *
 * The change applies to everyone who already redeemed the share: the settings
 * live on the share, and the redemption-facing endpoints read them per request.
 * On success the owner's subscribers are notified via
 * [UserSubscriptionMessage.EmittedSharesChanged] so open webapp sockets re-send
 * their emitted-share list.
 *
 * The outcome is an [UpdateShareResponse], so a rejected name arrives as its own
 * case (alongside `409 Conflict` / `400 Bad Request`) rather than as a generic
 * failure — exactly how creation reports the same two cases.
 */
fun Route.updateShare() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        patch {
            val actor = call.deviceActor(db)
                ?: return@patch call.respond(HttpStatusCode.Forbidden)
            val shareId = call.parameters["shareId"]?.let(Uuid::parseOrNull)
                ?: return@patch call.respond(HttpStatusCode.NotFound)

            val request = call.receive<UpdateShareRequest>()

            // Resolve + ownership-check + validate + mutate in one transaction.
            // Everything is validated before the first write: returning from the
            // lambda commits, so a late rejection would leave the earlier fields
            // applied.
            val result = db.transaction {
                val share = Share.findById(shareId) ?: return@transaction UpdateShareResponse.NotAllowed
                val device = share.device
                if (device.owner.id.value != actor.userId) return@transaction UpdateShareResponse.NotAllowed
                if (device.deletion != null) return@transaction UpdateShareResponse.NotAllowed

                val requestedName = request.shareName.getOrNull()?.trim()
                if (requestedName != null) {
                    if (requestedName.isEmpty()) return@transaction UpdateShareResponse.ShareNameEmpty
                    val isNameTaken = Shares
                        .innerJoin(Devices)
                        .select(Shares.id)
                        .where { Devices.owner eq device.owner.id }
                        .andWhere { Shares.shareName eq requestedName }
                        .andWhere { Shares.id neq share.id }
                        .count() > 0
                    if (isNameTaken) return@transaction UpdateShareResponse.ShareNameAlreadyExists
                }

                request.locationHistorySeconds.ifDefined { share.locationHistorySeconds = it }
                request.shareBatteryState.ifDefined { share.shareBatteryState = it }
                if (requestedName != null) {
                    share.shareName = requestedName
                }
                UpdateShareResponse.ShareUpdated
            }

            if (result is UpdateShareResponse.ShareUpdated) {
                userSubscriptionRepository.getFlowForUser(actor.userId)
                    .emit(UserSubscriptionMessage.EmittedSharesChanged(shareId))
            }

            call.respond<UpdateShareResponse>(
                message = result,
                status = when (result) {
                    UpdateShareResponse.ShareUpdated -> HttpStatusCode.OK
                    UpdateShareResponse.ShareNameAlreadyExists -> HttpStatusCode.Conflict
                    UpdateShareResponse.ShareNameEmpty -> HttpStatusCode.BadRequest
                    UpdateShareResponse.NotAllowed -> HttpStatusCode.Forbidden
                },
            )
        }
    }
}
