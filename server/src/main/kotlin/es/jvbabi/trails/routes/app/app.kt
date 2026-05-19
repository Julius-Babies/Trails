package routes.app

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.ShareSubscriptionMessage
import es.jvbabi.trails.data.ShareSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.Devices
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.sendSerialized
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private typealias ActiveShareId = Uuid
private typealias DeviceId = Uuid

fun Route.app() {

    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()
    val shareSubscriptionRepository by inject<ShareSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, optional = true) {
        webSocket("/ws") {
            val auth = call.principal<TrailsAppUserPrincipal>()

            val subscriptions = mutableMapOf<ActiveShareId, Job>()
            val ownDeviceSubscriptions = mutableMapOf<DeviceId, Job>()

            val selfFlow =
                if (auth != null) deviceSubscriptionRepository.getFlowForDeviceSubscription(db.transaction { auth.device.id.value }) else null

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = converter!!.deserialize<TrailsWebSocketAppMessage>(frame)
                    println(message)
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
                                    val share = db.transaction { ActiveShare.findById(id) }
                                    val device = db.transaction { share?.share?.device }

                                    subscriptions[id] = launch {
                                        shareSubscriptionRepository.getFlowForActiveShareSubscription(id)
                                            .collect { message ->
                                                sendSerialized<TrailsWebSocketServerMessage>(message.toSocketMessage(share, device!!))
                                            }
                                    }.also {
                                        it.invokeOnCompletion { subscriptions.remove(id) }
                                    }
                                }
                        }

                        is TrailsWebSocketAppMessage.SubscribeToOwn -> {
                            if (auth == null) continue
                            val ownDevices = db.transaction { auth.user.devices.toList() }
                            ownDevices.forEach { device ->
                                if (ownDeviceSubscriptions[device.id.value]?.isActive == true) return@forEach

                                ownDeviceSubscriptions[device.id.value] = launch {
                                    deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                                        .map { message -> shareSubscriptionRepository.shareProxy(null, message) }
                                        .collect { message ->
                                            sendSerialized<TrailsWebSocketServerMessage>(message.toSocketMessage(null, device))
                                        }
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
                }
            }
        }
    }
}

private const val MIN_DISTANCE_METERS = 10.0
private const val EARTH_RADIUS_METERS = 6371000.0

private fun ShareSubscriptionMessage.toSocketMessage(
    share: ActiveShare?,
    device: Device,
): TrailsWebSocketServerMessage {
    return when (this) {
        is ShareSubscriptionMessage.Deleted -> {
            TrailsWebSocketServerMessage.ShareDeleted(share!!.toString())
        }

        is ShareSubscriptionMessage.Snapshot -> {
            TrailsWebSocketServerMessage.Snapshot(
                target = if (share != null) TrailsWebSocketServerMessage.Snapshot.Target.Share(share.id.value.toString())
                        else TrailsWebSocketServerMessage.Snapshot.Target.Device(device.id.value.toString()),
                timestamp = time.epochSeconds,
                location = TrailsWebSocketServerMessage.Snapshot.Location(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    bearing = location.bearing,
                    bearingAccuracy = location.bearingAccuracy,
                    locationAccuracy = location.locationAccuracy,
                ),
                batteryState = batteryState?.let {
                    TrailsWebSocketServerMessage.Snapshot.BatteryState(
                        percentage = it.percentage,
                        isCharging = it.isCharging,
                    )
                }
            )
        }
    }
}

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

@Serializable
private sealed class TrailsWebSocketAppMessage {
    @SerialName("data_snapshot")
    @Serializable
    data class DataSnapshot(
        @SerialName("latitude") val latitude: Double,
        @SerialName("longitude") val longitude: Double,
        @SerialName("bearing") val bearing: Float,
        @SerialName("bearing_accuracy") val bearingAccuracy: Float?,
        @SerialName("location_accuracy") val locationAccuracy: Float,
        @SerialName("battery_level") val batteryLevel: Float?,
        @SerialName("battery_charging") val batteryCharging: Boolean?,
        @SerialName("time") val time: Long,
    ) : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("share.subscribe")
    data class ShareSubscribe(
        @SerialName("share_ids") val shareIds: List<String>,
    ) : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("own.subscribe")
    data object SubscribeToOwn : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("share.unsubscribe_all")
    data object ShareUnsubscribeAll : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("share.unsubscribe")
    data class ShareUnsubscribe(
        @SerialName("share_ids") val shareIds: List<String>,
    ) : TrailsWebSocketAppMessage()
}

@Serializable
private sealed class TrailsWebSocketServerMessage {
    @Serializable
    @SerialName("share.deleted")
    data class ShareDeleted(
        @SerialName("share_id") val shareId: String,
    ) : TrailsWebSocketServerMessage()

    @Serializable
    @SerialName("share.snapshot")
    data class Snapshot(
        @SerialName("target") val target: Target,
        @SerialName("timestamp") val timestamp: Long,
        @SerialName("location") val location: Location,
        @SerialName("battery_state") val batteryState: BatteryState?,
    ) : TrailsWebSocketServerMessage() {

        @Serializable
        sealed class Target {
            @Serializable
            @SerialName("share")
            data class Share(@SerialName("id") val shareId: String) : Target()

            @Serializable
            @SerialName("device")
            data class Device(@SerialName("id") val deviceId: String) : Target()
        }

        @Serializable
        data class Location(
            @SerialName("latitude") val latitude: Double,
            @SerialName("longitude") val longitude: Double,
            @SerialName("bearing") val bearing: Float,
            @SerialName("bearing_accuracy") val bearingAccuracy: Float?,
            @SerialName("location_accuracy") val locationAccuracy: Float,
        )

        @Serializable
        data class BatteryState(
            @SerialName("percentage") val percentage: Int,
            @SerialName("is_charging") val isCharging: Boolean,
        )
    }
}
