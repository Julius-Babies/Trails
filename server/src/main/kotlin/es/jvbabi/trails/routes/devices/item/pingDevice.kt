package es.jvbabi.trails.routes.devices.item

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.routes.devices.PingResult
import es.jvbabi.trails.routes.devices.pendingPings
import es.jvbabi.trails.shared.dto.PingDeviceResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Triggers a "find my device" ping on one of the caller's own devices and waits
 * up to five seconds for the device to acknowledge it. This is a generic REST
 * endpoint usable by both the app and the web (see [deviceActor]); it reuses the
 * shared [pendingPings] registry so a ping is acknowledged the same way
 * regardless of where it came from.
 */
fun Route.pingDevice() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        post {
            val actor = call.deviceActor(db)
                ?: return@post call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)
            val deviceId = call.parameters["deviceId"]?.let(Uuid::parseOrNull)
                ?: return@post call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)

            val device = db.transaction { Device.findById(deviceId) }
            if (device == null || db.transaction { device.owner.id.value != actor.userId }) {
                return@post call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)
            }

            val deferred = CompletableDeferred<PingResult>()
            pendingPings[deviceId] = deferred
            deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                .emit(DeviceSubscriptionMessage.Ping(device, pingedByDeviceName = actor.sourceName, pingedBySource = actor.source))

            val result = withTimeoutOrNull(5.seconds) { deferred.await() }
            pendingPings.remove(deviceId)

            call.respond<PingDeviceResponse>(
                if (result != null) PingDeviceResponse.Success(result.hasDeliveredNotification)
                else PingDeviceResponse.Timeout
            )
        }
    }
}
