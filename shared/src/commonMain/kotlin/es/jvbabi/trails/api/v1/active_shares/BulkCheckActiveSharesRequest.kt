package es.jvbabi.trails.api.v1.active_shares

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Asks a homeserver which of the given active shares still exist there. Used by
 * the app at start to prune saved shares whose redemption was returned/removed,
 * checking each origin directly (grouped per homeserver) rather than relying on
 * the account's saved references.
 */
@Serializable
data class BulkCheckActiveSharesRequest(
    @SerialName("active_share_ids") val activeShareIds: List<Uuid>,
)
