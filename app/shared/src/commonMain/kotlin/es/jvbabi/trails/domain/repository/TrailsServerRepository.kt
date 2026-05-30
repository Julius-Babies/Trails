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

    suspend fun useShareLink(hostname: String, id: String): UseShareLinkResult

    fun getConnectionEvents(server: String): Flow<List<ConnectionEvent>>

    suspend fun deleteDevice(device: Device): Result<Unit>
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
