package es.jvbabi.trails.api.v1.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Device(
    @SerialName("id") val id: Uuid,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("friendly_name") val friendlyName: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("owner_id") val ownerId: Uuid,
)
