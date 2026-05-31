package es.jvbabi.trails.shared.dto.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TrailsWebSocketAppMessage {
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
    @SerialName("appsocket.subscribe-shares")
    data class ShareSubscribe(
        @SerialName("share_ids") val shareIds: List<String>,
    ) : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("appsocket.unsubscribe-share")
    data class ShareUnsubscribe(
        @SerialName("share_ids") val shareIds: List<String>,
    ) : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("lifecycle.start-rt-updates")
    data object StartRtUpdates : TrailsWebSocketAppMessage()

    @Serializable
    @SerialName("lifecycle.stop-rt-updates")
    data object StopRtUpdates : TrailsWebSocketAppMessage()
}
