package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbUser
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.MeResponse
import es.jvbabi.trails.domain.repository.SnapshotRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UseShareLinkResult
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.asByteWriteChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TrailsServerRepositoryImpl(
    private val database: TrailsDatabase,
    private val httpClient: HttpClient,
    private val keyValueRepository: KeyValueRepository,
    private val snapshotRepository: SnapshotRepository,
    private val devicesRepository: DevicesRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
) : TrailsServerRepository {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag("TrailsServerRepositoryImpl")
    private var websocketSession: DefaultClientWebSocketSession? = null

    override val isConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun connect() {
        if (this.isConnected.value) return

        scope.launch {
            val url = this@TrailsServerRepositoryImpl.getBaseUrl().first()!!.apply {
                protocol = URLProtocol.WSS
                appendPathSegments("api", "v1", "app", "ws")
            }
            val token = keyValueRepository.get("trails.token").first()!!
            val currentDeviceId = keyValueRepository.get("trails.thisDeviceId").first() ?: throw IllegalStateException("Current device ID not set")
            val device = devicesRepository.getDeviceById(Uuid.parse(currentDeviceId)).first() ?: throw IllegalStateException("Current device not found in database")

            logger.i { "Connecting to WS at ${url.buildString()}" }

            try {
                websocketSession = httpClient.webSocketSession(
                    urlString = url.buildString()
                ) {
                    bearerAuth(token)
                }

                isConnected.value = true

                val locationUpdater = scope.launch {
                    snapshotRepository.getCurrentSnapshotForDevice(device)
                        .filterNotNull()
                        .distinctUntilChangedBy { location ->
                            location.copy(
                                time = Instant.DISTANT_PAST.toLocalDateTime(
                                    TimeZone.currentSystemDefault()
                                )
                            )
                        }
                        .takeWhile { isConnected.value }
                        .collectLatest {
                            val websocketSession = websocketSession ?: return@collectLatest
                            logger.i { "Sending location update: $it" }
                            websocketSession.sendSerialized<TrailsWebSocketAppMessage>(
                                TrailsWebSocketAppMessage.DataSnapshot(
                                    latitude = it.location.latitude,
                                    longitude = it.location.longitude,
                                    bearing = it.location.bearing,
                                    bearingAccuracy = it.location.bearingAccuracy,
                                    locationAccuracy = it.location.locationAccuracy,
                                    batteryLevel = it.batteryState?.percentage?.div(100f),
                                    batteryCharging = it.batteryState?.isCharging,
                                    time = it.time.toInstant(TimeZone.currentSystemDefault()).epochSeconds,
                                )
                            )
                        }
                }

                for (frame in websocketSession!!.incoming) {
                    if (frame is Frame.Text) logger.i { "Received WS message: ${frame.readText()}" }
                }
                locationUpdater.cancel()

                isConnected.value = false
                websocketSession?.close()
                websocketSession = null

            } catch (e: Exception) {
                Logger.e(e) { "Error connecting to WS: ${e.message}" }
                isConnected.value = false
            }

            delay(5.seconds)

            connect()
        }
    }

    override fun getBaseUrl(): Flow<URLBuilder?> {
        return keyValueRepository.get("trails.host")
            .map {
                if (it == null) null
                else URLBuilder(it.let {
                    if (it.startsWith("https://")) it
                    else "https://$it"
                })
            }
    }

    override fun getToken(): Flow<String?> {
        return keyValueRepository.get("trails.token")
    }

    override fun getUserId(): Flow<Uuid?> {
        return keyValueRepository.get("trails.userId")
            .map { it?.let { Uuid.parse(it) } }
    }

    override suspend fun getMeData(): MeResponse {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val url = (getBaseUrl().first() ?: throw IllegalStateException("Base URL not set")).apply {
            appendPathSegments("api", "v1", "me")
        }

        val response = httpClient.get(url.buildString()) {
            bearerAuth(token)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Error fetching me data: ${response.status}")
        }

        val body = response.body<MeResponse>()

        database.userDao.upsert(
            DbUser(
                id = Uuid.parse(body.id),
                homeserver = url.host,
                username = body.username,
            )
        )

        keyValueRepository.setValue("trails.userId", body.id)
        keyValueRepository.setValue("trails.thisDeviceId", body.thisDeviceId)

        return body
    }

    override suspend fun updateUserDevices() {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val userId = getUserId().first() ?: throw IllegalStateException("User ID not set")
        val url = (getBaseUrl().first() ?: throw IllegalStateException("Base URL not set")).apply {
            appendPathSegments("api", "v1", "me", "devices")
        }

        val response = httpClient.get(url.buildString()) {
            bearerAuth(token)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Error fetching devices: ${response.status}")
        }

        val body = response.body<List<DeviceResponse>>()

        body
            .map { DbDevice(
                id = Uuid.parse(it.id),
                manufacturer = it.manufacturer,
                model = it.model,
                friendlyName = it.friendlyName,
                displayName = it.displayName,
                ownerId = userId,
            ) }
            .let { database.deviceDao.upsertDevices(it) }

        devicesRepository.getDevices().first()
            .filterNot { devicesRepository.hasDeviceImage(it).first() }
            .forEach { fetchDeviceImageForDevice(it) }
    }

    override suspend fun fetchDeviceImageForDevice(device: Device) {
        val url = URLBuilder("https://${device.owner.homeserver}").apply {
            appendPathSegments("api", "v1", "devices", "image", "${device.manufacturer}-${device.model}")
        }

        val response = httpClient.get(url.buildString())
        if (!response.status.isSuccess()) {
            logger.w { "Device image not found for device ${device.id} at ${url.buildString()}" }
            return
        }
        val sink = fileRepository.getFileSink(devicesRepository.getFileNameForDeviceImage(device))
        response.bodyAsChannel().copyAndClose(sink.asByteWriteChannel())
    }

    override suspend fun useShareLink(hostname: String, id: String): UseShareLinkResult {
        val url = URLBuilder("https://$hostname").apply {
            appendPathSegments("api", "v1", "app", "share", "use")
        }

        val response = httpClient.post(url.buildString()) {
            contentType(ContentType.Application.Json)
            setBody(UseShareLinkRequest(id))
        }

        when (response.status) {
            HttpStatusCode.NotFound -> return UseShareLinkResult.NotExisting
            HttpStatusCode.Forbidden -> return UseShareLinkResult.Used
        }

        if (!response.status.isSuccess()) {
            Logger.e(NetworkRequestUnsuccessfulException(response)) { "Error using share link" }
            return UseShareLinkResult.Error("Error using share link: ${response.status}")
        }

        val body = response.body<UseShareLinkResponse>()
        database.userDao.upsert(DbUser(
            id = Uuid.parse(body.user.id),
            homeserver = hostname,
            username = body.user.username,
        ))

        database.deviceDao.upsertDevices(listOf(
            DbDevice(
                id = Uuid.parse(body.device.id),
                manufacturer = body.device.manufacturer,
                model = body.device.model,
                friendlyName = body.device.friendlyName,
                displayName = body.device.displayName,
                ownerId = Uuid.parse(body.user.id),
            )
        ))

        val device = devicesRepository.getDeviceById(Uuid.parse(body.device.id)).first() ?: throw IllegalStateException("Device not found after using share link")
        if (!devicesRepository.hasDeviceImage(device).first()) {
            fetchDeviceImageForDevice(device)
        }

        database.activeShareDao.upsert(DbActiveShare(
            id = Uuid.parse(body.shareId),
            deviceId = Uuid.parse(body.device.id),
        ))

        return UseShareLinkResult.Success
    }
}

@Serializable
private sealed class TrailsWebSocketAppMessage {
    @SerialName("data_snapshot")
    @Serializable
    data class DataSnapshot(
        @SerialName("latitude") val latitude: Double,
        @SerialName("longitude") val longitude: Double,
        @SerialName("bearing") val bearing: Float,
        @SerialName("bearing_accuracy") val bearingAccuracy: Float?,
        @SerialName("location_accuracy") val locationAccuracy: Float,
        @SerialName("battery_level") val batteryLevel: Float?,
        @SerialName("battery_charging") val batteryCharging: Boolean?,
        @SerialName("time") val time: Long,
    ) : TrailsWebSocketAppMessage()
}

@Serializable
private data class DeviceResponse(
    @SerialName("id") val id: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("friendly_name") val friendlyName: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class UseShareLinkRequest(
    @SerialName("id") val id: String,
)

@Serializable
private data class UseShareLinkResponse(
    @SerialName("share_token") val shareId: String,
    @SerialName("user") val user: User,
    @SerialName("device") val device: DeviceResponse,
) {
    @Serializable
    data class User(
        @SerialName("id") val id: String,
        @SerialName("username") val username: String,
    )
}
