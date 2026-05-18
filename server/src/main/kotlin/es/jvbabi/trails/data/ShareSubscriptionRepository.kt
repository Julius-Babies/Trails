package es.jvbabi.trails.data

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ShareSubscriptionRepository: KoinComponent {
    private val db by inject<DatabaseManager>()
    private val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    private val activeShareSubscriptions = mutableMapOf<Uuid, Flow<ShareSubscriptionMessage>>()

    suspend fun getFlowForActiveShareSubscription(shareSubscriptionId: Uuid): Flow<ShareSubscriptionMessage> {
        return activeShareSubscriptions.getOrPut(shareSubscriptionId) {
            val activeShare = db.transaction { ActiveShare.findById(shareSubscriptionId) }
            if (activeShare == null) {
                return@getOrPut flowOf(ShareSubscriptionMessage.Deleted)
            }

            val deviceId = db.transaction { activeShare.share.device.id.value }

            return@getOrPut deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                .map { message ->
                    when (message) {
                        DeviceSubscriptionMessage.Deleted -> ShareSubscriptionMessage.Deleted
                        is DeviceSubscriptionMessage.Snapshot -> {
                            val share = db.transaction { activeShare.share }
                            ShareSubscriptionMessage.Snapshot(
                                device = db.transaction { share.device },
                                time = message.snapshot.createdAt,
                                location = ShareSubscriptionMessage.Snapshot.Location(
                                    latitude = message.snapshot.latitude,
                                    longitude = message.snapshot.longitude,
                                    bearing = message.snapshot.bearing.toFloat(),
                                    bearingAccuracy = message.snapshot.bearingAccuracy?.toFloat(),
                                    locationAccuracy = message.snapshot.locationAccuracy.toFloat(),
                                ),
                                batteryState = when (share.shareBatteryState) {
                                    false -> null
                                    true -> {
                                        val batteryLevel = message.snapshot.batteryLevel
                                        val batteryCharging = message.snapshot.batteryCharging
                                        if (batteryLevel == null || batteryCharging == null) null
                                        else ShareSubscriptionMessage.Snapshot.BatteryState(
                                            percentage = (batteryLevel * 100).roundToInt(),
                                            isCharging = batteryCharging,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
        }
    }
}

sealed class ShareSubscriptionMessage {
    data object Deleted : ShareSubscriptionMessage()
    data class Snapshot(
        val device: Device,
        val time: Instant,
        val location: Location,
        val batteryState: BatteryState?,
    ):  ShareSubscriptionMessage()  {
        data class Location(
            val latitude: Double,
            val longitude: Double,
            val bearing: Float,
            val bearingAccuracy: Float?,
            val locationAccuracy: Float,
        )

        data class BatteryState(
            val percentage: Int,
            val isCharging: Boolean,
        )
    }
}
