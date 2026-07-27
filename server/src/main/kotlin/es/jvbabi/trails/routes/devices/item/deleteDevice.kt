package es.jvbabi.trails.routes.devices.item

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.DeviceDeletion
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * `DELETE /devices/{deviceId}` — soft-deletes one of the caller's own devices by
 * attaching a [DeviceDeletion]. Reachable by both the app and the web realm: an
 * app deletion records the acting device session in [DeviceDeletion.deletedBy], a
 * browser deletion leaves it null (surfaced as "Browser" to other clients).
 *
 * On success every subscriber is notified via [DeviceSubscriptionMessage.Deleted]
 * (so an affected device's own socket learns it was removed) and
 * [UserSubscriptionMessage.DeviceDeleted] (so the owner's other sessions drop it
 * from their device list live).
 */
fun Route.deleteDevice() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        delete {
            // Normalise both realms: the owning user is required, the device
            // session is only present for app deletions.
            val appPrincipal = call.principal<TrailsAppUserPrincipal>()
            val userId = appPrincipal?.user?.id?.value
                ?: call.principal<TrailsWebappPrincipal>()?.user?.id?.value
                ?: return@delete call.respond(HttpStatusCode.Forbidden)
            appPrincipal?.requireValidSession()

            val deviceId = call.parameters["deviceId"]?.let(Uuid::parseOrNull)
                ?: return@delete call.respond(HttpStatusCode.NotFound)

            // Resolve + ownership-check + mark deleted in one transaction; null
            // means the device is missing, not the caller's, or already deleted —
            // all answered as Forbidden.
            val deletion = db.transaction {
                val device = Device.findById(deviceId) ?: return@transaction null
                if (device.owner.id.value != userId) return@transaction null
                if (device.deletion != null) return@transaction null

                val deletion = DeviceDeletion.new {
                    this.device = device
                    this.deletedBy = appPrincipal?.session
                }
                device.deletion = deletion
                deletion
            } ?: return@delete call.respond(HttpStatusCode.Forbidden)

            deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                .emit(DeviceSubscriptionMessage.Deleted(deletion))
            userSubscriptionRepository.getFlowForUser(userId)
                .emit(UserSubscriptionMessage.DeviceDeleted(deletion))

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
