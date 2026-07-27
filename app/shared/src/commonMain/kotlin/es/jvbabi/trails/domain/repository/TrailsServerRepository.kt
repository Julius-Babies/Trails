package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.data.database.entity.ConnectionEvent
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.shared.dto.MeResponse
import io.ktor.http.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

interface TrailsServerRepository {

    fun connectWithHomeserver(): Deferred<Boolean>
    suspend fun connectWithOtherServer(server: String)
    suspend fun stopAllOtherServerConnections()

    fun isServerConnected(server: String): Flow<Boolean>
    val isConnected: StateFlow<Boolean>

    val isDeviceDeletedState: StateFlow<IsDeviceDeletedState>
    suspend fun resetDeviceDeletedState()

    fun getBaseUrl(): Flow<URLBuilder?>
    fun getToken(): Flow<String?>
    fun getUserId(): Flow<Uuid?>

    suspend fun checkSessionHealth(): SessionHealthState
    suspend fun getMeData(): Result<MeResponse>
    suspend fun updateUserDevices()
    suspend fun fetchDeviceImageForDevice(device: Device)

    suspend fun requestPing(device: Device): PingResult
    fun requestRing(device: Device)
    fun requestStopRing(device: Device)

    suspend fun useShareLink(hostname: String, id: String): UseShareLinkResult

    /**
     * Downloads the shares saved to the account from the homeserver and restores them
     * locally. No-op if there is no homeserver login.
     */
    suspend fun syncAccountShares()

    /**
     * Removes saved shares whose active share no longer exists on its origin
     * homeserver (returned/removed). Checks each origin directly, grouped per
     * homeserver, so it also covers shares that live only locally and never had an
     * account reference. A homeserver that can't be reached is left untouched.
     */
    suspend fun pruneRemovedShares()

    fun getConnectionEvents(server: String): Flow<List<ConnectionEvent>>

    suspend fun deleteDevice(device: Device): Result<Unit>

    /**
     * Renames [device]. A blank/`null` [customName] clears the custom name and
     * the server falls back to the model name.
     */
    suspend fun renameDevice(device: Device, customName: String?): Result<Unit>

    val ringStates: StateFlow<Map<Uuid, RingDeviceState>>
}

sealed class UseShareLinkResult {
    data object NotExisting : UseShareLinkResult()
    data object Used : UseShareLinkResult()
    data class Error(val message: String) : UseShareLinkResult()
    data object Success : UseShareLinkResult()
}

sealed class IsDeviceDeletedState {
    data object Unset : IsDeviceDeletedState()
    data class Deleted(val thisDevice: Device, val deletedByDeviceName: String): IsDeviceDeletedState()
}

sealed class SessionHealthState {
    data class Error(val errorMessage: String): SessionHealthState()
    data object InvalidOrExpired: SessionHealthState()
    data object Ok: SessionHealthState()
    data object NoSessionExpected: SessionHealthState()
}

sealed class PingResult {
    data class Pinged(val hasDeliveredNotification: Boolean): PingResult()
    data object Timeout: PingResult()
    data object NotAllowed: PingResult()
    data class Error(val errorMessage: String): PingResult()
}

data class RingDeviceState(
    val isRinging: Boolean,
    val ringedByDeviceName: String,
)
