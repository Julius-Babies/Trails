package es.jvbabi.trails.api.v1.me

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class RegisterUserShareRequest(
    @SerialName("share_id") val shareId: Uuid,
    @SerialName("homeserver") val homeserver: String,
)
