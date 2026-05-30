package es.jvbabi.trails.data

import database.DataSnapshot
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.DeviceDeletion
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import es.jvbabi.trails.routes.app.AppSocketMessage
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

class DeviceSubscriptionRepository : KoinComponent {
    private val db by inject<DatabaseManager>()

    private val deviceSubscriptions = mutableMapOf<Uuid, MutableSharedFlow<DeviceSubscriptionMessage>>()
    private val deviceSubscriptionsMutex = Mutex()

    suspend fun getFlowForDeviceSubscription(deviceId: Uuid): MutableSharedFlow<DeviceSubscriptionMessage> {
        return deviceSubscriptionsMutex.withLock {
            deviceSubscriptions.getOrPut(deviceId) {
                val device = db.transaction { Device.findById(deviceId) }
                val deviceDeletion = db.transaction { device?.deletion }
                if (device == null) {
                    return@getOrPut MutableSharedFlow()
                }
                if (deviceDeletion != null) {
                    return@getOrPut MutableStateFlow(DeviceSubscriptionMessage.Deleted(deviceDeletion))
                }

                return@getOrPut MutableSharedFlow()
            }
        }
    }
}

sealed class DeviceSubscriptionMessage : KoinComponent {
    data class Deleted(val deletion: DeviceDeletion) : DeviceSubscriptionMessage()
    data class Snapshot(val snapshot: DataSnapshot) : DeviceSubscriptionMessage()

    private val db by inject<DatabaseManager>()
    suspend fun toAppSocketMessage(
        principal: TrailsAppUserPrincipal?,
        share: ActiveShare?,
    ): AppSocketMessage {
        if (principal == null && share == null) throw RuntimeException("Must provide either a principal or a share")
        when (this) {
            is Deleted -> {
                return if (share != null) {
                    AppSocketMessage(
                        TrailsWebSocketServerMessage.ShareDeleted(
                            wasDeviceRemoved = true,
                            shareId = share.id.value.toString()
                        )
                    )
                } else {
                    val deviceId = db.transaction { deletion.device.id.value.toString() }
                    AppSocketMessage(
                        TrailsWebSocketServerMessage.DeviceDeleted(
                            deletedByDeviceName = db.transaction { deletion.deletedBy.device.displayName },
                            deviceId = deviceId
                        ),
                        closeConnectionAfterSending = db.transaction { principal!!.device.id.value == deletion.device.id.value }
                    )
                }
            }

            is Snapshot -> {
                val device = db.transaction { snapshot.device }
                val canAccessBatteryState =
                    (principal != null && db.transaction { snapshot.device.owner.id == principal.user.id }) ||
                            (share != null && db.transaction { share.share.shareBatteryState })

                val batteryLevel = snapshot.batteryLevel
                val batteryCharging = snapshot.batteryCharging

                return AppSocketMessage(TrailsWebSocketServerMessage.Snapshot(
                    target = if (share != null) TrailsWebSocketServerMessage.Snapshot.Target.Share(share.id.value.toString())
                    else TrailsWebSocketServerMessage.Snapshot.Target.Device(device.id.value.toString()),
                    timestamp = snapshot.createdAt.epochSeconds,
                    location = TrailsWebSocketServerMessage.Snapshot.Location(
                        latitude = snapshot.latitude,
                        longitude = snapshot.longitude,
                        bearing = snapshot.bearing.toFloat(),
                        bearingAccuracy = snapshot.bearingAccuracy?.toFloat(),
                        locationAccuracy = snapshot.locationAccuracy.toFloat(),
                    ),
                    batteryState = if (canAccessBatteryState && batteryCharging != null && batteryLevel != null) TrailsWebSocketServerMessage.Snapshot.BatteryState(
                        percentage = (batteryLevel * 100).roundToInt(),
                        isCharging = batteryCharging
                    ) else null
                ))
            }
        }
    }
}
