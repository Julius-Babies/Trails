package es.jvbabi.trails.routes.devices.item

import es.jvbabi.trails.Optional
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.isDefined
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
 * Generic partial update of one of the caller's own devices. Every field is an
 * [Optional]: an absent field is left untouched, a present one is applied. This
 * keeps the endpoint extensible — new device attributes just add another field.
 *
 * `custom_name` sets the device's user-chosen name. A null (or blank) value
 * clears it, resetting the name back to the model-derived fallback.
 */
@Serializable
data class UpdateDeviceRequest(
    @SerialName("custom_name") val customName: Optional<String?> = Optional.Undefined(),
)

/**
 * `PATCH /devices/{deviceId}` — lets the owning user update their device (see
 * [UpdateDeviceRequest]). Reachable by both the app and the web realm. On
 * success every subscriber (app + webapp sockets) is notified via
 * [UserSubscriptionMessage.DeviceUpdated] so the new name shows up live.
 */
fun Route.updateDevice() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        patch {
            val actor = call.deviceActor(db)
                ?: return@patch call.respond(HttpStatusCode.Forbidden)
            val deviceId = call.parameters["deviceId"]?.let(Uuid::parseOrNull)
                ?: return@patch call.respond(HttpStatusCode.NotFound)

            val request = call.receive<UpdateDeviceRequest>()

            // Resolve + mutate + ownership-check in one transaction; null means the
            // device is missing or not the caller's, both answered as Forbidden.
            val device = db.transaction {
                val device = Device.findById(deviceId) ?: return@transaction null
                if (device.owner.id.value != actor.userId) return@transaction null

                if (request.customName.isDefined()) {
                    val requested = request.customName.value?.trim()
                    device.displayName = if (requested.isNullOrEmpty()) {
                        "${device.manufacturer} ${device.friendlyName}"
                    } else requested
                }
                device
            } ?: return@patch call.respond(HttpStatusCode.Forbidden)

            userSubscriptionRepository.getFlowForUser(actor.userId)
                .emit(UserSubscriptionMessage.DeviceUpdated(device))

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
