package es.jvbabi.trails.routes.webapp.devices

import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsWebappPrincipal
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
 * Triggers a "find my device" ping on one of the current user's own devices and
 * waits up to five seconds for the device to acknowledge it.
 *
 * This mirrors the app socket's `device.ping.request` handling but is exposed as
 * a standalone webapp request so the ping/ring feature is not multiplexed onto
 * the device-update socket. It reuses the shared [pendingPings] registry, so a
 * ping issued from the web is acknowledged the same way as one from the app.
 */
fun Route.webappPingDevice() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_WEBAPP_REALM) {
        post {
            val user = call.principal<TrailsWebappPrincipal>()!!.user
            val deviceId = call.parameters["deviceId"]?.let(Uuid::parseOrNull)
                ?: return@post call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)

            val device = db.transaction { Device.findById(deviceId) }
            if (device == null || db.transaction { device.owner.id.value != user.id.value }) {
                return@post call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)
            }

            val deferred = CompletableDeferred<PingResult>()
            pendingPings[deviceId] = deferred
            deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                .emit(DeviceSubscriptionMessage.Ping(device, pingedByDeviceName = user.username))

            val result = withTimeoutOrNull(5.seconds) { deferred.await() }
            pendingPings.remove(deviceId)

            call.respond<PingDeviceResponse>(
                if (result != null) PingDeviceResponse.Success(result.hasDeliveredNotification)
                else PingDeviceResponse.Timeout
            )
        }
    }
}
