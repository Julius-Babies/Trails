package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShareResponse(
    @SerialName("share_id") val shareId: String,
)
