package es.jvbabi.trails.api.v1.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class CreateShareResponse(
    @SerialName("success") val success: Boolean,
) {

    @Serializable
    @SerialName("share-name-already-exists")
    data object ShareNameAlreadyExists: CreateShareResponse(false)

    @Serializable
    @SerialName("share-created")
    data class ShareCreated(
        @SerialName("share_id") val shareId: Uuid,
    ): CreateShareResponse(true)
}