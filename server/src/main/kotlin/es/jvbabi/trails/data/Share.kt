package es.jvbabi.trails.data

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DbShare
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface Share {
    val id: String
    val shareName: String
    val locationHistorySeconds: Int
    val shareBatteryState: Boolean
    val allowMultiuse: Boolean
    val isLocked: Boolean

    /** Resolves the device this share points at, federating when the share is
     * remote. [context] carries the capability that justifies the lookup. */
    fun getDevice(context: RequestContext = RequestContext.LOCAL): Flow<Device?>

    /** Resolves the user who owns (created) this share (share → device → owner),
     * federating when the share is remote. */
    fun getUser(context: RequestContext = RequestContext.LOCAL): Flow<User?>
}

class ShareId(from: String) {
    val host = from.substringBefore("/")
    val id = from.substringAfterLast("/").let(Uuid::parse)

    init {
        require(from.matches(Regex(".+/s/.+"))) { "Invalid share id format" }
    }
}

class LocalShare(
    dbShare: DbShare,
): Share, KoinComponent {
    private val applicationConfig by inject<ApplicationConfig>()
    private val deviceRepository by inject<DeviceRepository>()
    private val userRepository by inject<UserRepository>()

    override val id: String = "${applicationConfig.url.host}/s/${dbShare.id.value}"
    override val shareName: String = dbShare.shareName
    override val locationHistorySeconds: Int = dbShare.locationHistorySeconds
    override val shareBatteryState: Boolean = dbShare.shareBatteryState
    override val allowMultiuse: Boolean = dbShare.allowMultiuse
    override val isLocked: Boolean = dbShare.isLocked

    // Device and owner of a local share always live on this server. Captured
    // eagerly (inside the constructing transaction) as federated ids so resolution
    // goes through the repository proxies like any other entity.
    private val deviceId: String =
        "${applicationConfig.url.host}/d/${dbShare.device.id.value}"
    private val ownerId: String =
        "${applicationConfig.url.host}/u/${dbShare.device.owner.id.value}"

    override fun getDevice(context: RequestContext): Flow<Device?> = deviceRepository.getDevice(deviceId, context)

    override fun getUser(context: RequestContext): Flow<User?> = userRepository.getUser(ownerId, context)
}

abstract class RemoteShare(
    homeserver: String,
    shareId: String,
    deviceId: String,
): Share, KoinComponent {
    private val deviceRepository by inject<DeviceRepository>()

    override val id: String = "$homeserver/s/$shareId"
    private val deviceFederatedId: String = "$homeserver/d/$deviceId"

    override fun getDevice(context: RequestContext): Flow<Device?> =
        deviceRepository.getDevice(deviceFederatedId, context)

    // The owner is share → device → user; resolve the device first, then delegate
    // to it, forwarding the same capability.
    override fun getUser(context: RequestContext): Flow<User?> = flow {
        val device = getDevice(context).firstOrNull()
        emitAll(device?.getUser(context) ?: flowOf(null))
    }
}
