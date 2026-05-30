package es.jvbabi.trails.routes.app

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketAppMessage
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlin.uuid.Uuid

private typealias ActiveShareId = Uuid
private typealias DeviceId = Uuid

fun Route.app() {

    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, optional = true) {
        webSocket("/ws") {
            val logger = KtorSimpleLogger("AppWebSocket")
            val auth = call.principal<TrailsAppUserPrincipal>()
            auth?.requireValidSession()

            val subscriptions = mutableMapOf<ActiveShareId, Job>()
            val ownDeviceSubscriptions = mutableMapOf<DeviceId, Job>()

            val selfFlow =
                if (auth != null) deviceSubscriptionRepository.getFlowForDeviceSubscription(db.transaction { auth.device.id.value }) else null

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = converter!!.deserialize<TrailsWebSocketAppMessage>(frame)
                    try {
                        when (message) {
                            is TrailsWebSocketAppMessage.DataSnapshot -> {
                                if (auth == null) continue
                                launch {
                                    val snapshot = db.transaction {
                                        val existingSnapshot = DataSnapshot.find {
                                            DataSnapshots.device eq auth.device.id
                                        }.orderBy(DataSnapshots.createdAt to SortOrder.DESC)
                                            .limit(1)
                                            .firstOrNull()

                                        val batteryChanged = existingSnapshot?.let {
                                            it.batteryLevel != message.batteryLevel ||
                                                    it.batteryCharging != message.batteryCharging
                                        } ?: true

                                        val movedEnough = existingSnapshot?.let {
                                            distanceMeters(
                                                it.latitude,
                                                it.longitude,
                                                message.latitude,
                                                message.longitude,
                                            ) > MIN_DISTANCE_METERS
                                        } ?: true

                                        if (batteryChanged || movedEnough) {
                                            DataSnapshot.new {
                                                this.device = auth.device
                                                this.latitude = message.latitude
                                                this.longitude = message.longitude
                                                this.bearing = message.bearing.toDouble()
                                                this.bearingAccuracy = message.bearingAccuracy?.toDouble()
                                                this.locationAccuracy = message.locationAccuracy.toDouble()
                                                this.batteryLevel = message.batteryLevel
                                                this.batteryCharging = message.batteryCharging
                                                this.createdAt = Instant.fromEpochSeconds(message.time)
                                            }
                                        } else {
                                            existingSnapshot.createdAt = Instant.fromEpochSeconds(message.time)
                                            existingSnapshot
                                        }
                                    }

                                    if (selfFlow != null && selfFlow.subscriptionCount.value > 0) {
                                        selfFlow.emit(DeviceSubscriptionMessage.Snapshot(snapshot))
                                    }
                                }
                            }

                            is TrailsWebSocketAppMessage.ShareSubscribe -> {
                                message.shareIds
                                    .map { id -> Uuid.parse(id) }
                                    .forEach { id ->
                                        if (subscriptions[id]?.isActive == true) return@forEach
                                        val share = db.transaction { ActiveShare.findById(id) } ?: return@forEach
                                        val device = db.transaction { share.share.device }

                                        subscriptions[id] = launch {
                                            deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                                                .map { it.toAppSocketMessage(null, share) }
                                                .onEach { message ->
                                                    sendSerialized<TrailsWebSocketServerMessage>(message.message)
                                                }
                                                .takeWhile { !it.closeConnectionAfterSending }
                                                .collect()
                                            this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, ""))
                                        }.also {
                                            it.invokeOnCompletion { subscriptions.remove(id) }
                                        }
                                    }
                            }

                            is TrailsWebSocketAppMessage.SubscribeToOwn -> {
                                if (auth == null) continue
                                val ownDevices = db.transaction { auth.user.devices.toList().filter { it.deletion == null} }
                                ownDevices.forEach { device ->
                                    if (ownDeviceSubscriptions[device.id.value]?.isActive == true) return@forEach

                                    ownDeviceSubscriptions[device.id.value] = launch {
                                        deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                                            .map { it.toAppSocketMessage(auth, null) }
                                            .onEach { message ->
                                                sendSerialized<TrailsWebSocketServerMessage>(message.message)
                                            }
                                            .takeWhile { !it.closeConnectionAfterSending }
                                            .collect()
                                        this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, ""))
                                    }

                                }
                            }

                            is TrailsWebSocketAppMessage.ShareUnsubscribeAll -> {
                                subscriptions.forEach { it.value.cancel() }
                                ownDeviceSubscriptions.forEach { it.value.cancel() }
                            }

                            is TrailsWebSocketAppMessage.ShareUnsubscribe -> {
                                val unsubscribeIds = message.shareIds.map { Uuid.parse(it) }
                                subscriptions.filterKeys { it in unsubscribeIds }.forEach { it.value.cancel() }
                                ownDeviceSubscriptions.filterKeys { it in unsubscribeIds }.forEach { it.value.cancel() }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("""WebSocket message could not be handled:
                            |Message: $message
                            |Error: ${e.stackTraceToString()}
                        """.trimMargin())
                    }
                }
            }
        }
    }
}

private const val MIN_DISTANCE_METERS = 10.0
private const val EARTH_RADIUS_METERS = 6371000.0

private fun distanceMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val lat1 = Math.toRadians(latitude1)
    val lat2 = Math.toRadians(latitude2)
    val deltaLat = Math.toRadians(latitude2 - latitude1)
    val deltaLon = Math.toRadians(longitude2 - longitude1)

    val a = sin(deltaLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}



data class AppSocketMessage(
    val message: TrailsWebSocketServerMessage,
    val closeConnectionAfterSending: Boolean = false,
)