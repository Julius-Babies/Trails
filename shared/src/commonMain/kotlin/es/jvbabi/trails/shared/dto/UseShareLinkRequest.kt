package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UseShareLinkRequest(
    @SerialName("id") val id: String,
)
