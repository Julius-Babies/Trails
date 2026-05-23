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
