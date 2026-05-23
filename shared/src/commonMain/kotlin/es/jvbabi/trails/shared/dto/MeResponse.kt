package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("this_device_id") val thisDeviceId: String,
)
