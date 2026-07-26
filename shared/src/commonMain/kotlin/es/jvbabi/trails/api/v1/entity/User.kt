package es.jvbabi.trails.api.v1.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class User(
    @SerialName("id") val id: Uuid,
    @SerialName("username") val username: String,
)
