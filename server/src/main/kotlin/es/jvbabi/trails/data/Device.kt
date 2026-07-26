package es.jvbabi.trails.data

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.Device as DbDevice
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface Device {
    val id: String
    val manufacturer: String
    val model: String
    val friendlyName: String
    val displayName: String

    /** Resolves the owner of this device, federating to the owner's homeserver
     * when the device is remote. [context] carries the capability that justifies
     * the lookup on a foreign server. */
    fun getUser(context: RequestContext = RequestContext.LOCAL): Flow<User?>
}

class DeviceId(from: String) {
    val host = from.substringBefore("/")
    val id = from.substringAfterLast("/").let(Uuid::parse)

    init {
        require(from.matches(Regex(".+/d/.+"))) { "Invalid device id format" }
    }
}

class LocalDevice(
    dbDevice: DbDevice,
): Device, KoinComponent {
    private val applicationConfig by inject<ApplicationConfig>()
    private val userRepository by inject<UserRepository>()

    override val id: String = "${applicationConfig.url.host}/d/${dbDevice.id.value}"
    override val manufacturer: String = dbDevice.manufacturer
    override val model: String = dbDevice.model
    override val friendlyName: String = dbDevice.friendlyName
    override val displayName: String = dbDevice.displayName

    // The owner of a local device always lives on this server. Captured eagerly
    // (inside the constructing transaction) as a federated user id so resolution
    // goes through the [UserRepository] proxy like any other user.
    private val ownerId: String =
        "${applicationConfig.url.host}/u/${dbDevice.owner.id.value}"

    override fun getUser(context: RequestContext): Flow<User?> = userRepository.getUser(ownerId, context)
}

abstract class RemoteDevice(
    homeserver: String,
    deviceId: String,
    ownerId: String,
): Device, KoinComponent {
    private val userRepository by inject<UserRepository>()

    override val id: String = "$homeserver/d/$deviceId"
    private val ownerFederatedId: String = "$homeserver/u/$ownerId"

    override fun getUser(context: RequestContext): Flow<User?> =
        userRepository.getUser(ownerFederatedId, context)
}
