package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.Optional
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.isDefined
import es.jvbabi.trails.routes.devices.item.deviceActor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * Partial update of a share the caller emitted. Every field is an [Optional]:
 * an absent field is left untouched, a present one is applied — so new share
 * settings just add another field here.
 *
 * `location_history_seconds` follows the same encoding the share itself uses
 * (see `getActiveShareHistory`): `0` shares no history at all, a negative value
 * means an unbounded window, anything else is a window in seconds.
 */
@Serializable
data class UpdateShareRequest(
    @SerialName("location_history_seconds") val locationHistorySeconds: Optional<Int> = Optional.Undefined(),
    @SerialName("share_battery_state") val shareBatteryState: Optional<Boolean> = Optional.Undefined(),
)

/**
 * `PATCH /share/{shareId}` — lets the emitting user change a share's settings
 * (see [UpdateShareRequest]). Reachable by both the app and the web realm.
 *
 * The change applies to everyone who already redeemed the share: the settings
 * live on the share, and the redemption-facing endpoints read them per request.
 * On success the owner's subscribers are notified via
 * [UserSubscriptionMessage.EmittedSharesChanged] so open webapp sockets re-send
 * their emitted-share list.
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

            // Resolve + ownership-check + mutate in one transaction; null means the
            // share is missing or not the caller's, both answered as Forbidden so
            // the endpoint doesn't reveal which.
            val updated = db.transaction {
                val share = Share.findById(shareId) ?: return@transaction false
                val device = share.device
                if (device.owner.id.value != actor.userId) return@transaction false
                if (device.deletion != null) return@transaction false

                if (request.locationHistorySeconds.isDefined()) {
                    share.locationHistorySeconds = request.locationHistorySeconds.value
                }
                if (request.shareBatteryState.isDefined()) {
                    share.shareBatteryState = request.shareBatteryState.value
                }
                true
            }
            if (!updated) return@patch call.respond(HttpStatusCode.Forbidden)

            userSubscriptionRepository.getFlowForUser(actor.userId)
                .emit(UserSubscriptionMessage.EmittedSharesChanged(shareId))

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
