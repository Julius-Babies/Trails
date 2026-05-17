package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.Device
import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

interface TrailsServerRepository {

    fun connect()

    val isConnected: StateFlow<Boolean>

    fun getBaseUrl(): Flow<URLBuilder?>
    fun getToken(): Flow<String?>
    fun getUserId(): Flow<Uuid?>

    suspend fun getMeData(): MeResponse
    suspend fun updateUserDevices()
    suspend fun fetchDeviceImageForDevice(device: Device)
}

@Serializable
data class MeResponse(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("this_device_id") val thisDeviceId: String,
)