package es.jvbabi.trails.api.v1.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class RedeemShareResponse {
    @Serializable
    @SerialName("err-share-locked")
    data object ShareLocked: RedeemShareResponse()

    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("active_share_id") val activeShareId: Uuid,
    ): RedeemShareResponse()
}