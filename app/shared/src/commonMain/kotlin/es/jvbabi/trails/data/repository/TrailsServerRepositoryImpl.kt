package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbUser
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.shared.dto.DeviceResponse
import es.jvbabi.trails.shared.dto.MeResponse
import es.jvbabi.trails.shared.dto.UseShareLinkRequest
import es.jvbabi.trails.shared.dto.UseShareLinkResponse
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketAppMessage
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.domain.repository.SnapshotRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UseShareLinkResult
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.converter
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
import io.ktor.serialization.deserialize
import io.ktor.utils.io.asByteWriteChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TrailsServerRepositoryImpl(
    private val database: TrailsDatabase,
    private val httpClient: HttpClient,
    private val keyValueRepository: KeyValueRepository,
    private val snapshotRepository: SnapshotRepository,
    private val devicesRepository: DevicesRepository,
    private val shareRepository: ShareRepository,
    private val applicationRepository: ApplicationRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
) : TrailsServerRepository {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag("TrailsServerRepositoryImpl")
    private var websocketSession: DefaultClientWebSocketSession? = null
    private val homeServerSocketClient = HomeServerWebSocketClient(
        scope = scope,
        applicationRepository = applicationRepository,
        shareRepository = shareRepository,
        snapshotRepository = snapshotRepository,
        devicesRepository = devicesRepository,
        database = database,
        logger = logger,
    )
    private val externalServerSocketClient = ExternalServerWebSocketClient(
        scope = scope,
        applicationRepository = applicationRepository,
        shareRepository = shareRepository,
        snapshotRepository = snapshotRepository,
        devicesRepository = devicesRepository,
        database = database,
        logger = logger,
    )

    override val isConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun connectWithHomeserver(): Deferred<Boolean> {
        if (this.isConnected.value) return CompletableDeferred(true)

        val connectedDeferred = CompletableDeferred<Boolean>()

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
                connectedDeferred.complete(true)

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

                val serverHost = getBaseUrl().first()!!.host
                homeServerSocketClient.run(websocketSession!!, serverHost)

                locationUpdater.cancel()

                isConnected.value = false
                websocketSession?.close()
                websocketSession = null

            } catch (e: Exception) {
                Logger.e(e) { "Error connecting to WS: ${e.message}" }
                isConnected.value = false
            }

            delay(5.seconds)

            val reconnectResult = connectWithHomeserver().await()
            if (!connectedDeferred.isCompleted) connectedDeferred.complete(reconnectResult)
        }

        return connectedDeferred
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
        val user = userRepository.getUser(userId).firstOrNull() ?: throw IllegalStateException("User not found in database")
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

        val ownDevices = devicesRepository.getDevices(user).first()
        devicesRepository.removeDevices(ownDevices.filter { device -> body.none { it.id == device.id.toString() } })
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

    typealias ServerHost = String
    private val activeExternalSessions = mutableMapOf<ServerHost, DefaultClientWebSocketSession>()

    override suspend fun connectWithOtherServer(server: String) {
        if (activeExternalSessions[server]?.isActive == true) return
        val url = URLBuilder("wss://$server").apply {
            appendPathSegments("api", "v1", "app", "ws")
        }

        try {
            Logger.i { "Connecting with external server $server" }
            activeExternalSessions[server] = httpClient.webSocketSession(urlString = url.buildString())

            externalServerSocketClient.run(activeExternalSessions[server]!!, server)

            activeExternalSessions[server]?.close()
            activeExternalSessions.remove(server)

        } catch (e: Exception) {
            Logger.e(e) { "Error connecting to WS: ${e.message}" }
        }

        delay(5.seconds)

        connectWithOtherServer(server)
    }

    override suspend fun stopAllOtherServerConnections() {
        activeExternalSessions.map {
            scope.launch { it.value.close(); activeExternalSessions.remove(it.key) }
        }.joinAll()
    }
}

private abstract class WebSocketClientBase(
    protected val scope: CoroutineScope,
    protected val applicationRepository: ApplicationRepository,
    protected val shareRepository: ShareRepository,
    protected val snapshotRepository: SnapshotRepository,
    protected val devicesRepository: DevicesRepository,
    protected val database: TrailsDatabase,
    protected val logger: Logger,
) {
    suspend fun run(session: DefaultClientWebSocketSession, serverHost: String) {
        val appForegroundSyncer = startShareSubscriptionSync(serverHost) { session }
        handleIncomingMessages(session)
        appForegroundSyncer.cancel()
    }

    private fun startShareSubscriptionSync(
        serverHost: String,
        sessionProvider: () -> DefaultClientWebSocketSession?
    ) = scope.launch {
        applicationRepository.getApplicationForegroundState().collectLatest { inForeground ->
            if (inForeground) {
                sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(
                    TrailsWebSocketAppMessage.SubscribeToOwn
                )
                val subscribedShares = mutableSetOf<Uuid>()
                shareRepository.getShares()
                    .map { it.filter { share -> share.device.owner.homeserver == serverHost } }
                    .map { it.toSet() }
                    .distinctUntilChanged()
                    .collectLatest { shares ->
                        val newShareIds = shares.map { it.id }.toSet() - subscribedShares
                        sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(
                            TrailsWebSocketAppMessage.ShareSubscribe(newShareIds.map { it.toString() })
                        )
                        subscribedShares.addAll(newShareIds)

                        val removedShareIds = subscribedShares - shares.map { it.id }.toSet()
                        sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(
                            TrailsWebSocketAppMessage.ShareUnsubscribe(removedShareIds.map { it.toString() })
                        )
                        subscribedShares.removeAll(removedShareIds)
                    }
            } else {
                sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.ShareUnsubscribeAll)
            }
        }
    }

    private suspend fun handleIncomingMessages(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val message = session.converter!!.deserialize<TrailsWebSocketServerMessage>(frame)
                logger.i { "Received WS message: $message" }

                when (message) {
                    is TrailsWebSocketServerMessage.ShareDeleted -> {
                        database.activeShareDao.deleteById(Uuid.parse(message.shareId))
                    }

                    is TrailsWebSocketServerMessage.Snapshot -> {
                        val device = when (val target = message.target) {
                            is TrailsWebSocketServerMessage.Snapshot.Target.Device -> devicesRepository.getDeviceById(Uuid.parse(target.deviceId)).firstOrNull()
                            is TrailsWebSocketServerMessage.Snapshot.Target.Share -> shareRepository.getShareById(Uuid.parse(target.shareId)).firstOrNull()?.device
                        }
                        if (device == null) {
                            logger.w { "Received snapshot for unknown device in WS message: $message" }
                            continue
                        }
                        val timestamp = Instant.fromEpochSeconds(message.timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        snapshotRepository.storeSnapshot(
                            Snapshot(
                                device = device,
                                time = timestamp,
                                location = Location(
                                    latitude = message.location.latitude,
                                    longitude = message.location.longitude,
                                    bearing = message.location.bearing,
                                    bearingAccuracy = message.location.bearingAccuracy,
                                    locationAccuracy = message.location.locationAccuracy,
                                    time = timestamp,
                                ),
                                batteryState = message.batteryState?.let {
                                    BatteryState(
                                        percentage = it.percentage,
                                        isCharging = it.isCharging,
                                    )
                                },
                            )
                        )
                    }
                }
            }
        }
    }
}

private class HomeServerWebSocketClient(
    scope: CoroutineScope,
    applicationRepository: ApplicationRepository,
    shareRepository: ShareRepository,
    snapshotRepository: SnapshotRepository,
    devicesRepository: DevicesRepository,
    database: TrailsDatabase,
    logger: Logger,
) : WebSocketClientBase(
    scope = scope,
    applicationRepository = applicationRepository,
    shareRepository = shareRepository,
    snapshotRepository = snapshotRepository,
    devicesRepository = devicesRepository,
    database = database,
    logger = logger,
)

private class ExternalServerWebSocketClient(
    scope: CoroutineScope,
    applicationRepository: ApplicationRepository,
    shareRepository: ShareRepository,
    snapshotRepository: SnapshotRepository,
    devicesRepository: DevicesRepository,
    database: TrailsDatabase,
    logger: Logger,
) : WebSocketClientBase(
    scope = scope,
    applicationRepository = applicationRepository,
    shareRepository = shareRepository,
    snapshotRepository = snapshotRepository,
    devicesRepository = devicesRepository,
    database = database,
    logger = logger,
)
