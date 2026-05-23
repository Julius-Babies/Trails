package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    @SerialName("id") val id: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("friendly_name") val friendlyName: String,
    @SerialName("display_name") val displayName: String,
)
