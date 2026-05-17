package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.Device
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

interface TrailsServerRepository {

    fun connectWithHomeserver(): Deferred<Boolean>
    suspend fun connectWithOtherServer(server: String)
    suspend fun stopAllOtherServerConnections()

    val isConnected: StateFlow<Boolean>

    fun getBaseUrl(): Flow<URLBuilder?>
    fun getToken(): Flow<String?>
    fun getUserId(): Flow<Uuid?>

    suspend fun getMeData(): MeResponse
    suspend fun updateUserDevices()
    suspend fun fetchDeviceImageForDevice(device: Device)

    suspend fun useShareLink(hostname: String, id: String): UseShareLinkResult
}

@Serializable
data class MeResponse(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("this_device_id") val thisDeviceId: String,
)

sealed class UseShareLinkResult {
    data object NotExisting : UseShareLinkResult()
    data object Used : UseShareLinkResult()
    data class Error(val message: String) : UseShareLinkResult()
    data object Success : UseShareLinkResult()
}