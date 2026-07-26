package es.jvbabi.trails.api.v1.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ActiveShare(
    @SerialName("id") val id: Uuid,
    @SerialName("share_id") val shareId: Uuid,
)
