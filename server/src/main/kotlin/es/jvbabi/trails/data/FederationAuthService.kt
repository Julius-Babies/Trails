package es.jvbabi.trails.data

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Validates federated read requests using the capability model: the presented
 * [RequestContext.activeShareId] is a bearer capability scoped to exactly the
 * active share it names and the chain below it (share → device → owner). A
 * request is authorized only when the requested entity lies within that chain.
 *
 * The capability is always resolved locally — it is presented *to* the server
 * that minted (and owns) the active share.
 *
 * Note: this is the capability check only. It does not authenticate the calling
 * server, so a leaked capability is usable by anyone who holds it (accepted
 * trade-off of the bearer-capability model).
 */
class FederationAuthService: KoinComponent {
    private val db by inject<DatabaseManager>()

    /** Resolve the capability and evaluate [block] against it in one transaction. */
    private suspend fun <T> withCapability(context: RequestContext, block: (ActiveShare) -> T): T? {
        val raw = context.activeShareId ?: return null
        val id = Uuid.parseOrNull(raw) ?: return null
        return db.transaction { ActiveShare.findById(id)?.let(block) }
    }

    /** The active share the capability authorizes, or null if absent/invalid. */
    suspend fun capability(context: RequestContext): ActiveShare? =
        withCapability(context) { it }

    suspend fun mayReadActiveShare(context: RequestContext, activeShareId: Uuid): Boolean =
        withCapability(context) { it.id.value == activeShareId } ?: false

    suspend fun mayReadShare(context: RequestContext, shareId: Uuid): Boolean =
        withCapability(context) { it.share.id.value == shareId } ?: false

    suspend fun mayReadDevice(context: RequestContext, deviceId: Uuid): Boolean =
        withCapability(context) { it.share.device.id.value == deviceId } ?: false

    suspend fun mayReadUser(context: RequestContext, userId: Uuid): Boolean =
        withCapability(context) { it.share.device.owner.id.value == userId } ?: false
}
