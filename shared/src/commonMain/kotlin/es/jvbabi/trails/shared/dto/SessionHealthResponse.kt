package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SessionHealthResponse {
    @Serializable
    @SerialName("device.deleted")
    data class DeviceDeleted(
        @SerialName("deleted_by_device_name") val deletedByDeviceName: String,
    ): SessionHealthResponse()

    @Serializable
    @SerialName("valid")
    data object Valid: SessionHealthResponse()
}