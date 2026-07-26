package es.jvbabi.trails.data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

/** HTTP header carrying the active-share capability on federated requests. */
const val FEDERATION_ACTIVE_SHARE_HEADER = "Trails-Active-Share"

/**
 * The justification for a repository lookup — "why do we want this info".
 *
 * When a lookup has to cross to a foreign Trails server, federation must not mean
 * "everything is public": the requesting server has to present a capability proving
 * it may read the data. That capability is the [activeShareId] the share was
 * redeemed into (the same id used to register and read the share).
 *
 * Local lookups ignore the context — the owning server already holds the data — so
 * it is optional there; [LOCAL] represents "no capability presented".
 */
data class RequestContext(
    val activeShareId: String? = null,
) {
    /** Attach the capability to an outgoing federated request, if present. */
    fun applyTo(builder: HttpRequestBuilder) {
        activeShareId?.let { builder.header(FEDERATION_ACTIVE_SHARE_HEADER, it) }
    }

    companion object {
        val LOCAL = RequestContext()
    }
}
