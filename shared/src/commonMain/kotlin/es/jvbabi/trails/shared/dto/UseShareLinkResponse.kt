package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UseShareLinkResponse(
    @SerialName("share_token") val shareId: String,
    @SerialName("user") val user: User,
    @SerialName("device") val device: DeviceResponse,
) {
    @Serializable
    data class User(
        @SerialName("id") val id: String,
        @SerialName("username") val username: String,
    )
}
