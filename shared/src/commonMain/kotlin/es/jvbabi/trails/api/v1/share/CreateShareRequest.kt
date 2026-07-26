package es.jvbabi.trails.api.v1.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateShareRequest(
    @SerialName("device_id") val deviceId: Uuid,
    @SerialName("location_history_seconds") val locationHistorySeconds: Int,
    @SerialName("share_battery") val shareBattery: Boolean,
    @SerialName("share_name") val shareName: String,
    @SerialName("allow_multiuse") val allowMultiuse: Boolean,
)