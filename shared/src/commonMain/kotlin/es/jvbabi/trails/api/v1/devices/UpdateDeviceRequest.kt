package es.jvbabi.trails.api.v1.devices

import es.jvbabi.trails.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic partial update of one of the caller's own devices. Every field is an
 * [Optional]: an absent field is left untouched, a present one is applied. This
 * keeps the endpoint extensible — new device attributes just add another field.
 *
 * `custom_name` sets the device's user-chosen name. A null (or blank) value
 * clears it, resetting the name back to the model-derived fallback.
 */
@Serializable
data class UpdateDeviceRequest(
    @SerialName("custom_name") val customName: Optional<String?> = Optional.Undefined(),
)
