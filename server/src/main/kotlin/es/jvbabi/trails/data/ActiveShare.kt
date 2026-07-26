package es.jvbabi.trails.data

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.ActiveShare as DbActiveShare
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface ActiveShare {
    val id: String

    /** Resolves the share this active share redeemed, federating when the active
     * share lives on a foreign homeserver. [context] carries the capability. */
    fun getShare(context: RequestContext = RequestContext.LOCAL): Flow<Share?>
}

class ActiveShareId(from: String) {
    val host = from.substringBefore("/")
    val id = from.substringAfterLast("/").let(Uuid::parse)

    init {
        require(from.matches(Regex(".+/a/.+"))) { "Invalid active share id format" }
    }
}

class LocalActiveShare(
    dbActiveShare: DbActiveShare,
): ActiveShare, KoinComponent {
    private val applicationConfig by inject<ApplicationConfig>()
    private val shareRepository by inject<ShareRepository>()

    override val id: String = "${applicationConfig.url.host}/a/${dbActiveShare.id.value}"

    private val shareId: String =
        "${applicationConfig.url.host}/s/${dbActiveShare.share.id.value}"

    override fun getShare(context: RequestContext): Flow<Share?> = shareRepository.getShare(shareId, context)
}

abstract class RemoteActiveShare(
    homeserver: String,
    activeShareId: String,
    shareId: String,
): ActiveShare, KoinComponent {
    private val shareRepository by inject<ShareRepository>()

    override val id: String = "$homeserver/a/$activeShareId"
    private val shareFederatedId: String = "$homeserver/s/$shareId"

    override fun getShare(context: RequestContext): Flow<Share?> =
        shareRepository.getShare(shareFederatedId, context)
}
