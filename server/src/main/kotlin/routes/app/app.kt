package routes.app

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.ShareSubscriptionMessage
import es.jvbabi.trails.data.ShareSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
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

fun Route.app() {

    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()
    val shareSubscriptionRepository by inject<ShareSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, optional = true) {
        webSocket("/ws") {
            val auth = call.principal<TrailsAppUserPrincipal>()

            val subscriptions = mutableMapOf<ActiveShareId, Job>()

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

                                    subscriptions[id] = launch {
                                        shareSubscriptionRepository.getFlowForActiveShareSubscription(id)
                                            .collect { message ->
                                                when (message) {
                                                    is ShareSubscriptionMessage.Deleted -> {
                                                        sendSerialized<TrailsWebSocketServerMessage>(
                                                            TrailsWebSocketServerMessage.ShareDeleted(id.toString())
                                                        )
                                                    }

                                                    is ShareSubscriptionMessage.Snapshot -> {
                                                        sendSerialized<TrailsWebSocketServerMessage>(
                                                            TrailsWebSocketServerMessage.Snapshot(
                                                                shareId = id.toString(),
                                                                timestamp = message.time.toEpochMilliseconds(),
                                                                location = TrailsWebSocketServerMessage.Snapshot.Location(
                                                                    latitude = message.location.latitude,
                                                                    longitude = message.location.longitude,
                                                                    bearing = message.location.bearing,
                                                                    bearingAccuracy = message.location.bearingAccuracy,
                                                                    locationAccuracy = message.location.locationAccuracy,
                                                                ),
                                                                batteryState = message.batteryState?.let {
                                                                    TrailsWebSocketServerMessage.Snapshot.BatteryState(
                                                                        percentage = it.percentage,
                                                                        isCharging = it.isCharging,
                                                                    )
                                                                }
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                    }.also {
                                        it.invokeOnCompletion { subscriptions.remove(id) }
                                    }
                                }
                        }

                        is TrailsWebSocketAppMessage.ShareUnsubscribeAll -> {
                            subscriptions.forEach { it.value.cancel() }
                        }

                        is TrailsWebSocketAppMessage.ShareUnsubscribe -> {
                            val unsubscribeIds = message.shareIds.map { Uuid.parse(it) }
                            subscriptions.filterKeys { it in unsubscribeIds }.forEach { it.value.cancel() }
                        }
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
        @SerialName("share_id") val shareId: String,
        @SerialName("timestamp") val timestamp: Long,
        @SerialName("location") val location: Location,
        @SerialName("battery_state") val batteryState: BatteryState?,
    ) : TrailsWebSocketServerMessage() {

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
