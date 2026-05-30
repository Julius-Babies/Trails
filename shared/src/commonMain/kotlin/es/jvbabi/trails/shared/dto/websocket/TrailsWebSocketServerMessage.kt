package es.jvbabi.trails.shared.dto.websocket

import es.jvbabi.trails.shared.dto.DeviceResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TrailsWebSocketServerMessage {
    @Serializable
    @SerialName("device.deleted")
    data class DeviceDeleted(
        @SerialName("deleted_by_device_name") val deletedByDeviceName: String,
        @SerialName("device_id") val deviceId: String,
    ): TrailsWebSocketServerMessage()

    @Serializable
    @SerialName("device.updated")
    data class DeviceUpdated(
        @SerialName("data") val data: DeviceResponse,
    ) : TrailsWebSocketServerMessage()

    @Serializable
    @SerialName("share.deleted")
    data class ShareDeleted(
        @SerialName("was_device_removed") val wasDeviceRemoved: Boolean,
        @SerialName("share_id") val shareId: String,
    ): TrailsWebSocketServerMessage()

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
