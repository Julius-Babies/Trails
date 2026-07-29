package es.jvbabi.trails.api.v1.share

import es.jvbabi.trails.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Partial update of a share the caller emitted. Every field is an [Optional]:
 * an absent field is left untouched, a present one is applied — so new share
 * settings just add another field here.
 *
 * `location_history_seconds` follows the same encoding the share itself uses
 * (see `getActiveShareHistory`): `0` shares no history at all, a negative value
 * means an unbounded window, anything else is a window in seconds.
 *
 * `share_name` is trimmed and — exactly like on creation — has to be non-blank
 * and unique among the caller's shares.
 */
@Serializable
data class UpdateShareRequest(
    @SerialName("location_history_seconds") val locationHistorySeconds: Optional<Int> = Optional.Undefined(),
    @SerialName("share_battery_state") val shareBatteryState: Optional<Boolean> = Optional.Undefined(),
    @SerialName("share_name") val shareName: Optional<String> = Optional.Undefined(),
)
