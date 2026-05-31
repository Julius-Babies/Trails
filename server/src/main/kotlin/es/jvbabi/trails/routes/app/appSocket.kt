package es.jvbabi.trails.routes.app

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.routes.devices.PingResult
import es.jvbabi.trails.routes.devices.pendingPings
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketAppMessage
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, optional = true) {
        webSocket("/ws") {
            val principal = call.principal<TrailsAppUserPrincipal>()
            principal?.requireValidSession()

            val appSocketLogger = KtorSimpleLogger("AppWebSocket")

            val subscribedShares = mutableSetOf<ActiveShareId>()
            val shareSubscriptionRtUpdaters = mutableMapOf<ActiveShareId, Job>()
            val ownDeviceSubscriptionRtUpdaters = mutableMapOf<DeviceId, Job>()

            val selfFlow =
                if (principal != null) deviceSubscriptionRepository.getFlowForDeviceSubscription(db.transaction { principal.device.id.value }) else null

            val emitRtUpdates = MutableStateFlow(true)

            suspend fun startShareSubscription(shareId: ActiveShareId) {
                if (shareSubscriptionRtUpdaters[shareId]?.isActive == true) return
                val share = db.transaction { ActiveShare.findById(shareId) } ?: return
                val device = db.transaction { share.share.device }

                shareSubscriptionRtUpdaters[shareId] = launch {
                    deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                        .mapNotNull { it.toAppSocketMessage(null, share) }
                        .filterNot { it.message is TrailsWebSocketServerMessage.Snapshot && !emitRtUpdates.value }
                        .onEach { message ->
                            sendSerialized<TrailsWebSocketServerMessage>(message.message)
                        }
                        .takeWhile { !it.closeConnectionAfterSending }
                        .collect()
                    this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, ""))
                }.also {
                    it.invokeOnCompletion { shareSubscriptionRtUpdaters.remove(shareId) }
                }
            }

            suspend fun startOwnDeviceSubscription(deviceId: DeviceId) {
                requireNotNull(principal) { "Cannot subscribe to own device without a principal" }

                val device = db.transaction { Device.findById(deviceId) } ?: return

                if (ownDeviceSubscriptionRtUpdaters[device.id.value]?.isActive == true) return

                ownDeviceSubscriptionRtUpdaters[device.id.value] = launch {
                    deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                        .mapNotNull { it.toAppSocketMessage(principal, null) }
                        .filterNot { it.message is TrailsWebSocketServerMessage.Snapshot && !emitRtUpdates.value }
                        .onEach { message ->
                            sendSerialized<TrailsWebSocketServerMessage>(message.message)
                        }
                        .takeWhile { !it.closeConnectionAfterSending }
                        .collect()
                    this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, ""))
                }
            }

            if (principal != null) {
                val ownDevices = db.transaction { principal.user.devices.toList().filter { it.deletion == null } }
                ownDevices.forEach { startOwnDeviceSubscription(it.id.value) }
            }

            launch(CoroutineName("SharesRtUpdates")) {
                subscribedShares.forEach { startShareSubscription(it) }
            }

            launch(CoroutineName("ThisUserEvents")) {
                if (principal == null) return@launch
                userSubscriptionRepository.getFlowForUser(principal.user.id.value)
                    .onEach { message ->
                        when (message) {
                            is UserSubscriptionMessage.DeviceUpdated -> {
                                startOwnDeviceSubscription(message.device.id.value)
                            }
                            is UserSubscriptionMessage.DeviceDeleted -> {
                                val deviceId = db.transaction { message.deletion.device.id.value }
                                ownDeviceSubscriptionRtUpdaters[deviceId]?.cancel()
                                ownDeviceSubscriptionRtUpdaters.remove(deviceId)
                            }
                        }
                    }
                    .mapNotNull { it.toAppSocketMessage(principal) }
                    .onEach { this@webSocket.sendSerialized(it.message) }
                    .takeWhile { !it.closeConnectionAfterSending && this@webSocket.isActive }
                    .collect()
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = converter!!.deserialize<TrailsWebSocketAppMessage>(frame)
                    try {
                        println(message)
                        when (message) {
                            is TrailsWebSocketAppMessage.DataSnapshot -> {
                                if (principal == null) continue
                                launch {
                                    val snapshot = db.transaction {
                                        val existingSnapshot = DataSnapshot.find {
                                            DataSnapshots.device eq principal.device.id
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
                                                this.device = principal.device
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
                                        if (!subscribedShares.contains(id)) {
                                            subscribedShares.add(id)
                                        }
                                        startShareSubscription(id)
                                    }
                            }

                            is TrailsWebSocketAppMessage.ShareUnsubscribe -> {
                                val unsubscribeIds = message.shareIds.map { Uuid.parse(it) }
                                shareSubscriptionRtUpdaters.filterKeys { it in unsubscribeIds }.forEach { it.value.cancel() }
                            }

                            is TrailsWebSocketAppMessage.StartRtUpdates -> emitRtUpdates.value = true
                            is TrailsWebSocketAppMessage.StopRtUpdates -> emitRtUpdates.value = false

                            is TrailsWebSocketAppMessage.Pong -> {
                                if (principal == null) continue
                                val deferred = pendingPings[principal.device.id.value] ?: continue
                                deferred.complete(PingResult(message.hasDeliveredNotification))
                            }
                        }
                    } catch (e: Exception) {
                        appSocketLogger.error("""WebSocket message could not be handled:
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