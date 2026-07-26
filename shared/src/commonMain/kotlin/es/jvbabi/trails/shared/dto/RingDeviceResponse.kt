package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RingDeviceResponse {
    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("has_ringing_started") val hasRingingStarted: Boolean,
    ): RingDeviceResponse()

    @Serializable
    @SerialName("error")
    data class Error(
        @SerialName("message") val message: String
    ): RingDeviceResponse()

    @Serializable
    @SerialName("forbidden")
    data object Forbidden: RingDeviceResponse()

    @Serializable
    @SerialName("timeout")
    data object Timeout: RingDeviceResponse()
}