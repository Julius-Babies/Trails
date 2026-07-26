package es.jvbabi.trails.api.v1.me

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class EmittedShareResponse(
    @SerialName("id") val id: Uuid,
    @SerialName("device_id") val deviceId: Uuid,
    @SerialName("share_name") val shareName: String,
    @SerialName("location_history_seconds") val locationHistorySeconds: Int,
    @SerialName("share_battery_state") val shareBatteryState: Boolean,
    @SerialName("allow_multiuse") val allowMultiuse: Boolean,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("redemption_count") val redemptionCount: Long,
)
