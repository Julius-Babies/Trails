package es.jvbabi.trails.api.v1.active_shares

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The subset of the requested active shares that still exist on this homeserver
 * (present and their device not deleted). Any requested id missing from this list
 * has been returned/removed and the caller should drop it locally.
 */
@Serializable
data class BulkCheckActiveSharesResponse(
    @SerialName("existing_active_share_ids") val existingActiveShareIds: List<Uuid>,
)
