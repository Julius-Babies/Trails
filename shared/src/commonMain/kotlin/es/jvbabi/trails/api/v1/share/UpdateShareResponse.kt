package es.jvbabi.trails.api.v1.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outcome of `PATCH /share/{shareId}`. Mirrors [CreateShareResponse]: a share
 * name follows the same rules on edit as on creation, so a client can tell a
 * rejected name from a real failure and report it on the input field.
 *
 * [NotAllowed] covers a missing share as well as someone else's — the endpoint
 * deliberately does not say which.
 */
@Serializable
sealed class UpdateShareResponse(
    @SerialName("success") val success: Boolean,
) {

    @Serializable
    @SerialName("share-updated")
    data object ShareUpdated: UpdateShareResponse(true)

    @Serializable
    @SerialName("share-name-already-exists")
    data object ShareNameAlreadyExists: UpdateShareResponse(false)

    @Serializable
    @SerialName("share-name-empty")
    data object ShareNameEmpty: UpdateShareResponse(false)

    @Serializable
    @SerialName("not-allowed")
    data object NotAllowed: UpdateShareResponse(false)
}
