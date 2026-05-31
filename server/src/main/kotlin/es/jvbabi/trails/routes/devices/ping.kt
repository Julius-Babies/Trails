package es.jvbabi.trails.routes.devices

import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.shared.dto.PingDeviceResponse
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

val pendingPings = mutableMapOf<Uuid, CompletableDeferred<PingResult>>()

data class PingResult(
    val hasDeliveredNotification: Boolean
)

fun Route.ping() {

    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    post("/ping") {
        val device = call.attributes[deviceKey]
        val principal = call.principal<TrailsAppUserPrincipal>()!!
        principal.requireValidSession()
        if (db.transaction { device.owner.id.value != principal.user.id.value }) {
            call.respond<PingDeviceResponse>(PingDeviceResponse.Forbidden)
            return@post
        }

        val deferred = CompletableDeferred<PingResult>()
        pendingPings[device.id.value] = deferred

        val deviceFlow = deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
        deviceFlow.emit(DeviceSubscriptionMessage.Ping(device, pingedByDeviceName = principal.device.displayName))

        val result = withTimeoutOrNull(5.seconds) {
            deferred.await()
        }

        if (result == null) {
            pendingPings.remove(device.id.value)
            call.respond<PingDeviceResponse>(PingDeviceResponse.Timeout)
        }
        else call.respond<PingDeviceResponse>(PingDeviceResponse.Success(result.hasDeliveredNotification))
    }
}