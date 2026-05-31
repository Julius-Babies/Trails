package es.jvbabi.trails.routes.devices

import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.shared.dto.PingDeviceResponse
import es.jvbabi.trails.shared.dto.RingDeviceResponse
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

val pendingRings = mutableMapOf<Uuid, MutableSharedFlow<RingEvent>>()

sealed class RingEvent {
    data object Started: RingEvent()
    data object Stopped: RingEvent()
}

fun Route.ring() {

    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    sse("/ring", serialize = { typeInfo, it ->
        val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
        Json.encodeToString(serializer, it)
    }) {
        heartbeat {
            period = 10.seconds
            event = ServerSentEvent("heartbeat")
        }

        val device = call.attributes[deviceKey]
        val principal = call.principal<TrailsAppUserPrincipal>()!!
        principal.requireValidSession()
        if (db.transaction { device.owner.id.value != principal.user.id.value }) {
            send<RingDeviceResponse>(RingDeviceResponse.Forbidden)
            return@sse
        }

        val flow = MutableSharedFlow<RingEvent>()
        pendingRings[device.id.value] = flow

        val deviceFlow = deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
        deviceFlow.emit(DeviceSubscriptionMessage.Ring(device, pingedByDeviceName = principal.device.displayName))

        val hasRingingStarted = withTimeoutOrNull(5.seconds) {
            flow.filterIsInstance<RingEvent.Started>().firstOrNull()
        } != null

        if (!hasRingingStarted) {
            pendingRings.remove(device.id.value)
            send<RingDeviceResponse>(RingDeviceResponse.Timeout)
            return@sse
        }
        send<RingDeviceResponse>(RingDeviceResponse.Success(hasRingingStarted = true))
        withTimeout(30.seconds) {
            flow.filterIsInstance<RingEvent.Stopped>().first()
        }
    }
}