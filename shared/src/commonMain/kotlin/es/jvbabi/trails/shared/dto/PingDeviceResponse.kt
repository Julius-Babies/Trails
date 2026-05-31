package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PingDeviceResponse {
    @Serializable
    @SerialName("success")
    data class Success(
        val hasDeliveredNotification: Boolean,
    ): PingDeviceResponse()

    @Serializable
    @SerialName("error")
    data class Error(
        @SerialName("message") val message: String
    ): PingDeviceResponse()

    @Serializable
    @SerialName("forbidden")
    data object Forbidden: PingDeviceResponse()

    @Serializable
    @SerialName("timeout")
    data object Timeout: PingDeviceResponse()
}