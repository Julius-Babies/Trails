package es.jvbabi.trails.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewShareRequest(
    @SerialName("history_duration_seconds") val historyDurationSeconds: Int,
    @SerialName("battery_state") val batteryState: Boolean,
    @SerialName("share_name") val shareName: String,
    @SerialName("allow_multiuse") val allowMultiuse: Boolean,
)
