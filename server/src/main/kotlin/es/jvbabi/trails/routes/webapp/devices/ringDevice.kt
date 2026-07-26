package es.jvbabi.trails.routes.webapp.devices

import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.routes.app.deviceRingInfo
import es.jvbabi.trails.shared.dto.RingDeviceResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * Starts ringing one of the current user's own devices. Like [webappPingDevice]
 * this is a standalone webapp request rather than a socket message, and reuses
 * the same device-subscription flow the app uses so the target device rings
 * regardless of whether the request came from the app or the web.
 */
fun Route.webappRingDevice() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_WEBAPP_REALM) {
        post {
            val user = call.principal<TrailsWebappPrincipal>()!!.user
            val device = call.ownDeviceOrNull(db, user.id.value)
                ?: return@post call.respond<RingDeviceResponse>(RingDeviceResponse.Forbidden)

            deviceRingInfo[device.id.value] = user.username
            deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                .emit(DeviceSubscriptionMessage.Ring(device, pingedByDeviceName = user.username))

            call.respond<RingDeviceResponse>(RingDeviceResponse.Success(hasRingingStarted = true))
        }
    }
}

/**
 * Stops a ring previously started on one of the current user's own devices.
 */
fun Route.webappStopRingDevice() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_WEBAPP_REALM) {
        post {
            val user = call.principal<TrailsWebappPrincipal>()!!.user
            val device = call.ownDeviceOrNull(db, user.id.value)
                ?: return@post call.respond<RingDeviceResponse>(RingDeviceResponse.Forbidden)

            deviceRingInfo.remove(device.id.value)
            deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                .emit(DeviceSubscriptionMessage.RingStop(device))

            call.respond<RingDeviceResponse>(RingDeviceResponse.Success(hasRingingStarted = false))
        }
    }
}

/**
 * Resolves the `{deviceId}` path parameter to a device owned by [userId], or
 * `null` if the id is missing/invalid or the device is not owned by the user.
 */
private suspend fun ApplicationCall.ownDeviceOrNull(db: DatabaseManager, userId: Uuid): Device? {
    val deviceId = parameters["deviceId"]?.let(Uuid::parseOrNull) ?: return null
    val device = db.transaction { Device.findById(deviceId) } ?: return null
    return if (db.transaction { device.owner.id.value == userId }) device else null
}
