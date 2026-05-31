package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.ConnectionEvent
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbConnectionEvent
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbUser
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.shared.dto.DeviceResponse
import es.jvbabi.trails.shared.dto.MeResponse
import es.jvbabi.trails.shared.dto.PingDeviceResponse
import es.jvbabi.trails.shared.dto.RingDeviceResponse
import es.jvbabi.trails.shared.dto.SessionHealthResponse
import es.jvbabi.trails.shared.dto.UseShareLinkRequest
import es.jvbabi.trails.shared.dto.UseShareLinkResponse
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketAppMessage
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TrailsServerRepositoryImpl(
    private val database: TrailsDatabase,
    private val httpClient: HttpClient,
    private val keyValueRepository: KeyValueRepository,
    private val snapshotRepository: SnapshotRepository,
    private val devicesRepository: DevicesRepository,
    private val deviceRepository: DeviceRepository,
    private val shareRepository: ShareRepository,
    private val applicationRepository: ApplicationRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    private val notificationRepository: NotificationRepository,
) : TrailsServerRepository {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag("TrailsServerRepositoryImpl")
    private var websocketSession: DefaultClientWebSocketSession? = null
    private val crashDetectionMarkers = mutableMapOf<String, Uuid>()
    private val crashDetectionJobs = mutableMapOf<String, Job>()
    private val homeServerSocketClient = HomeServerWebSocketClient(
        scope = scope,
        applicationRepository = applicationRepository,
        shareRepository = shareRepository,
        snapshotRepository = snapshotRepository,
        devicesRepository = devicesRepository,
        keyValueRepository = keyValueRepository,
        userRepository = userRepository,
        deviceRepository = deviceRepository,
        notificationRepository = notificationRepository,
        trailsServerRepositoryImpl = this,
        database = database,
        logger = logger,
    )
    private val externalServerSocketClient = ExternalServerWebSocketClient(
        scope = scope,
        applicationRepository = applicationRepository,
        shareRepository = shareRepository,
        snapshotRepository = snapshotRepository,
        devicesRepository = devicesRepository,
        keyValueRepository = keyValueRepository,
        deviceRepository = deviceRepository,
        userRepository = userRepository,
        notificationRepository = notificationRepository,
        trailsServerRepositoryImpl = this,
        database = database,
        logger = logger,
    )

    override val isConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val isDeviceDeletedState: StateFlow<IsDeviceDeletedState>
        field = MutableStateFlow<IsDeviceDeletedState>(IsDeviceDeletedState.Unset)

    fun setDeviceDeletedState(state: IsDeviceDeletedState) {
        isDeviceDeletedState.value = state
    }

    override suspend fun resetDeviceDeletedState() {
        val deletedState = isDeviceDeletedState.value
        if (deletedState is IsDeviceDeletedState.Deleted) {
            database.deviceDao.deleteDevicesByIds(listOf(deletedState.thisDevice.id))
            keyValueRepository.delete("trails.thisDeviceId")
            keyValueRepository.delete("trails.userId")
            keyValueRepository.delete("trails.host")
        }
        isDeviceDeletedState.value = IsDeviceDeletedState.Unset
    }

    override fun isServerConnected(server: String): Flow<Boolean> = combine(
        isConnected,
        getBaseUrl().map { it?.host }.distinctUntilChanged()
    ) { homeConnected, homeHost ->
        homeHost == server && homeConnected || activeExternalSessions[server]?.isActive == true
    }.distinctUntilChanged()

    private var homeServerConnectJob: Job? = null

    override fun connectWithHomeserver(): Deferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        if (this.isConnected.value || homeServerConnectJob?.isActive == true) {
            deferred.complete(this.isConnected.value)
            return deferred
        }

        homeServerConnectJob = scope.launch {
            var currentRetry = 0
            while (isActive) {
                var wasConnected = false
                var locationUpdater: Job? = null
                var currentServerHost: String? = null
                try {
                    val url = this@TrailsServerRepositoryImpl.getBaseUrl().first()?.apply {
                        protocol = URLProtocol.WSS
                        appendPathSegments("api", "v1", "app", "ws")
                    } ?: throw IllegalStateException("Base URL not set")
                    currentServerHost = url.host

                    val token = keyValueRepository.get("trails.token").first()
                        ?: throw IllegalStateException("Token not set")
                    val currentDeviceId = keyValueRepository.get("trails.thisDeviceId").first()
                        ?: throw IllegalStateException("Current device ID not set")
                    val device = runCatching { devicesRepository.getDeviceById(Uuid.parse(currentDeviceId)).first() }
                        .getOrNull() ?: throw IllegalStateException("Current device not found in database")

                    logger.i { "Connecting to WS at ${url.buildString()}" }

                    websocketSession = httpClient.webSocketSession(
                        urlString = url.buildString()
                    ) {
                        bearerAuth(token)
                    }

                    isConnected.value = true
                    wasConnected = true
                    if (!deferred.isCompleted) deferred.complete(true)

                    database.connectionEventDao.upsert(
                        ConnectionEvent(
                            id = Uuid.random(),
                            server = url.host,
                            timestamp = Clock.System.now(),
                            data = ConnectionEvent.Event.Connected
                        ).toEntity()
                    )
                    startCrashDetection(url.host)

                    locationUpdater = scope.launch {
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
                                val ws = websocketSession ?: return@collectLatest
                                logger.i { "Sending location update: $it" }
                                ws.sendSerialized<TrailsWebSocketAppMessage>(
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

                    homeServerSocketClient.run(websocketSession!!, url.host)

                    locationUpdater.cancel()
                    stopCrashDetection(url.host)

                    isConnected.value = false
                    websocketSession?.close()
                    websocketSession = null

                    database.connectionEventDao.upsert(ConnectionEvent(
                        id = Uuid.random(),
                        server = url.host,
                        timestamp = Clock.System.now(),
                        data = ConnectionEvent.Event.Disconnected
                    ).toEntity())

                } catch (e: Exception) {
                    Logger.e(e) { "Error connecting to WS: ${e.message}" }
                    locationUpdater?.cancel()
                    if (currentServerHost != null) stopCrashDetection(currentServerHost)
                    isConnected.value = false
                    database.connectionEventDao.upsert(ConnectionEvent(
                        id = Uuid.random(),
                        server = currentServerHost ?: "unknown",
                        timestamp = Clock.System.now(),
                        data = ConnectionEvent.Event.Disconnected
                    ).toEntity())
                }

                val maxRetries = 30
                if (!wasConnected) {
                    if (currentRetry < maxRetries) {
                        val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                            1_000L
                        } else {
                            minOf(30_000L, 5_000L * (1L shl currentRetry))
                        }
                        delay(delayMs.milliseconds)
                        currentRetry++
                    } else {
                        if (!deferred.isCompleted) deferred.complete(false)
                        break
                    }
                } else {
                    if (!deferred.isCompleted) deferred.complete(true)
                    val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                        1_000L
                    } else {
                        5_000L
                    }
                    delay(delayMs.milliseconds)
                    currentRetry = 0
                }
            }
        }
        return deferred
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
            .map { it?.let { id -> runCatching { Uuid.parse(id) }.getOrNull() } }
    }

    override suspend fun checkSessionHealth(): SessionHealthState {
        val token = getToken().first() ?: return SessionHealthState.NoSessionExpected
        val url = (getBaseUrl().first() ?: return SessionHealthState.NoSessionExpected).apply {
            appendPathSegments("api", "v1", "app", "session-healthcheck")
        }

        val response = httpClient.get(url.buildString()) {
            bearerAuth(token)
        }

        if (!response.status.isSuccess()) {
            return SessionHealthState.Error("Error checking session health: ${response.status} ${response.bodyAsText()}")
        }

        when (val data = response.body<SessionHealthResponse>()) {
            is SessionHealthResponse.DeviceDeleted -> {
                val thisDeviceId = keyValueRepository.get("trails.thisDeviceId").first() ?: return SessionHealthState.NoSessionExpected
                val thisDevice = devicesRepository.getDeviceById(Uuid.parse(thisDeviceId)).firstOrNull() ?: return SessionHealthState.NoSessionExpected
                isDeviceDeletedState.update { IsDeviceDeletedState.Deleted(thisDevice = thisDevice, deletedByDeviceName = data.deletedByDeviceName) }
                return SessionHealthState.InvalidOrExpired
            }
            is SessionHealthResponse.Valid -> return SessionHealthState.Ok
        }
    }

    override suspend fun getMeData(): Result<MeResponse> {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val url = (getBaseUrl().first() ?: throw IllegalStateException("Base URL not set")).apply {
            appendPathSegments("api", "v1", "me")
        }

        val response = httpClient.get(url.buildString()) {
            bearerAuth(token)
        }

        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.Unauthorized) {
                keyValueRepository.delete("trails.token")
                keyValueRepository.delete("trails.userId")
                keyValueRepository.delete("trails.thisDeviceId")
                keyValueRepository.delete("trails.host")

                return Result.failure(IllegalStateException("Token expired"))
            }
            return Result.failure(IllegalStateException("Error fetching me data: ${response.status} ${response.bodyAsText()}"))
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

        return Result.success(body)
    }

    override suspend fun updateUserDevices() {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val userId = getUserId().first() ?: throw IllegalStateException("User ID not set")
        val user = userRepository.getUser(userId).firstOrNull() ?: throw IllegalStateException("User not found in database")
        val url = (getBaseUrl().first() ?: throw IllegalStateException("Base URL not set")).apply {
            appendPathSegments("api", "v1", "devices")
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

    override suspend fun pingDevice(device: Device): PingResult {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val url = URLBuilder("https://${device.owner.homeserver}").apply {
            appendPathSegments("api", "v1", "devices", device.id.toString(), "ping")
        }

        val response = httpClient.post(url.buildString()) {
            bearerAuth(token)
        }

        return when (val body = response.body<PingDeviceResponse>()) {
            is PingDeviceResponse.Success -> PingResult.Pinged(body.hasDeliveredNotification)
            is PingDeviceResponse.Forbidden -> PingResult.NotAllowed
            is PingDeviceResponse.Timeout -> PingResult.Timeout
            is PingDeviceResponse.Error -> PingResult.Error(body.message)
        }
    }

    override suspend fun ringDevice(device: Device): RingResult {
        val token = getToken().first() ?: throw IllegalStateException("Token not set")
        val url = URLBuilder("https://${device.owner.homeserver}").apply {
            appendPathSegments("api", "v1", "devices", device.id.toString(), "ring")
        }

        val result = CompletableDeferred<RingResult>()

        httpClient.sse(urlString = url.buildString(), request = {
            bearerAuth(token)
        }) {
            val first = incoming.first()
            val message = Json.decodeFromString<RingDeviceResponse>(first.data!!)

            when (message) {
                is RingDeviceResponse.Success -> {
                    val ringFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
                    ringFlow.tryEmit(true)
                    result.complete(RingResult.Ringed(ringFlow.asSharedFlow()))
                    incoming.collect { }
                    ringFlow.tryEmit(false)
                }
                is RingDeviceResponse.Forbidden -> result.complete(RingResult.NotAllowed)
                is RingDeviceResponse.Timeout -> result.complete(RingResult.Timeout)
                is RingDeviceResponse.Error -> result.complete(RingResult.Error(message.message))
            }
        }

        return result.await()
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

    override suspend fun connectWithOtherServer(server: String) = connectWithOtherServer(server, 0)

    private suspend fun connectWithOtherServer(server: String, retryCount: Int) {
        var currentRetry = retryCount
        while (currentCoroutineContext().isActive) {
            if (activeExternalSessions[server]?.isActive == true) return
            val url = URLBuilder("wss://$server").apply {
                appendPathSegments("api", "v1", "app", "ws")
            }

            var wasConnected = false
            try {
                Logger.i { "Connecting with external server $server" }
                activeExternalSessions[server] = httpClient.webSocketSession(urlString = url.buildString())

                database.connectionEventDao.upsert(ConnectionEvent(
                    id = Uuid.random(),
                    server = url.host,
                    timestamp = Clock.System.now(),
                    data = ConnectionEvent.Event.Connected,
                ).toEntity())
                startCrashDetection(server)
                wasConnected = true

                externalServerSocketClient.run(activeExternalSessions[server]!!, server)
                stopCrashDetection(server)

                database.connectionEventDao.upsert(ConnectionEvent(
                    id = Uuid.random(),
                    server = url.host,
                    timestamp = Clock.System.now(),
                    data = ConnectionEvent.Event.Disconnected
                ).toEntity())

                activeExternalSessions[server]?.close()
                activeExternalSessions.remove(server)

            } catch (e: Exception) {
                Logger.e(e) { "Error connecting to WS: ${e.message}" }
                stopCrashDetection(server)
                database.connectionEventDao.upsert(ConnectionEvent(
                    id = Uuid.random(),
                    server = url.host,
                    timestamp = Clock.System.now(),
                    data = ConnectionEvent.Event.Disconnected
                ).toEntity())
            }

            val maxRetries = 30
            if (!wasConnected) {
                if (currentRetry < maxRetries) {
                    val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                        1_000L
                    } else {
                        minOf(30_000L, 5_000L * (1L shl currentRetry))
                    }
                    delay(delayMs.milliseconds)
                    currentRetry++
                } else {
                    break
                }
            } else {
                val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                    1_000L
                } else {
                    5_000L
                }
                delay(delayMs.milliseconds)
                currentRetry = 0
            }
        }
    }

    override suspend fun stopAllOtherServerConnections() {
        activeExternalSessions.map {
            scope.launch { it.value.close(); activeExternalSessions.remove(it.key) }
        }.joinAll()
    }

    private suspend fun startCrashDetection(server: String) {
        val markerId = Uuid.random()
        crashDetectionMarkers[server] = markerId
        database.connectionEventDao.upsert(
            ConnectionEvent(
                id = markerId,
                server = server,
                timestamp = Clock.System.now() + 2.seconds,
                data = ConnectionEvent.Event.Disconnected,
            ).toEntity()
        )
        crashDetectionJobs[server] = scope.launch {
            while (isActive) {
                delay(1.seconds)
                database.connectionEventDao.upsert(
                    ConnectionEvent(
                        id = markerId,
                        server = server,
                        timestamp = Clock.System.now() + 2.seconds,
                        data = ConnectionEvent.Event.Disconnected,
                    ).toEntity()
                )
            }
        }
    }

    private fun stopCrashDetection(server: String) {
        crashDetectionJobs[server]?.cancel()
        crashDetectionJobs.remove(server)
        crashDetectionMarkers.remove(server)?.let { markerId ->
            scope.launch { database.connectionEventDao.delete(markerId) }
        }
    }

    override fun getConnectionEvents(server: String): Flow<List<ConnectionEvent>> {
        return database.connectionEventDao.getEvents(server).map { events ->
            val connectionEvents = events.map(DbConnectionEvent::toModel)
            val now = Clock.System.now()
            val latestDisconnect = connectionEvents.firstOrNull { it.data is ConnectionEvent.Event.Disconnected }
            if (latestDisconnect != null && latestDisconnect.timestamp - now > 0.seconds) {
                connectionEvents.filterNot { it.id == latestDisconnect.id }
            } else connectionEvents
        }
    }

    override suspend fun deleteDevice(device: Device): Result<Unit> {
        val url = URLBuilder("https://${device.owner.homeserver}").apply {
            appendPathSegments("api", "v1", "devices", device.id.toString())
        }
        val token = getToken().first() ?: throw IllegalStateException("Token not set")

        val response = httpClient.delete(url.buildString()) {
            bearerAuth(token)
        }

        if (response.status.isSuccess()) {
            database.deviceDao.deleteDevicesByIds(listOf(device.id))
            return Result.success(Unit)
        }

        return Result.failure(IllegalStateException("Error deleting device: ${response.status} ${response.bodyAsText()}"))
    }
}

private abstract class WebSocketClientBase(
    protected val scope: CoroutineScope,
    protected val applicationRepository: ApplicationRepository,
    protected val shareRepository: ShareRepository,
    protected val snapshotRepository: SnapshotRepository,
    protected val devicesRepository: DevicesRepository,
    protected val deviceRepository: DeviceRepository,
    protected val trailsServerRepositoryImpl: TrailsServerRepositoryImpl,
    protected val userRepository: UserRepository,
    protected val keyValueRepository: KeyValueRepository,
    protected val notificationRepository: NotificationRepository,
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
        val subscribedShares = mutableSetOf<Uuid>()
        launch {
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
        }
        launch {
            if (applicationRepository.getApplicationForegroundState().first()) {
                sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.StartRtUpdates)
            }
            applicationRepository.getApplicationForegroundState().collectLatest { inForeground ->
                if (inForeground) {
                    sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.StartRtUpdates)
                } else {
                    sessionProvider()?.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.StopRtUpdates)
                }
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
                        runCatching { Uuid.parse(message.shareId) }.getOrNull()?.let { database.activeShareDao.deleteById(it) }
                    }

                    is TrailsWebSocketServerMessage.Ping -> {
                        val notificationSent = notificationRepository.sendNotification(
                            channelId = NotificationRepository.PING_CHANNEL_ID,
                            title = "Gerät gefunden",
                            body = "Dein ${message.pingedByDeviceName} hat dieses Gerät gefunden!",
                            notificationId = message.pingedByDeviceName.hashCode()
                        )
                        session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.Pong(notificationSent))
                    }

                    is TrailsWebSocketServerMessage.Ring -> {
                        deviceRepository.startRinging(
                            causedByDeviceName = message.ringedByDeviceName,
                            onStop = { scope.launch { session.sendSerialized(TrailsWebSocketAppMessage.RingStop) } }
                        )
                        session.sendSerialized(TrailsWebSocketAppMessage.RingStart)
                    }

                    is TrailsWebSocketServerMessage.DeviceUpdated -> {
                        val userId = Uuid.parse(message.data.ownerId)
                        userRepository.getUser(userId).firstOrNull() ?: continue
                        val deviceId = Uuid.parse(message.data.id)
                        database.deviceDao.upsertDevices(listOf(DbDevice(
                            id = deviceId,
                            manufacturer = message.data.manufacturer,
                            friendlyName = message.data.friendlyName,
                            displayName = message.data.displayName,
                            model = message.data.model,
                            ownerId = userId
                        )))
                    }

                    is TrailsWebSocketServerMessage.DeviceDeleted -> {
                        val deletedDeviceId = Uuid.parse(message.deviceId)
                        val thisDeviceId = keyValueRepository.get("trails.thisDeviceId").firstOrNull()?.let(Uuid::parse)
                        if (thisDeviceId == deletedDeviceId) {
                            val thisDevice = devicesRepository.getDeviceById(thisDeviceId).firstOrNull() ?: continue
                            trailsServerRepositoryImpl.setDeviceDeletedState(IsDeviceDeletedState.Deleted(
                                thisDevice = thisDevice,
                                deletedByDeviceName = message.deletedByDeviceName,
                            ))
                        } else {
                            database.deviceDao.deleteDevicesByIds(listOf(deletedDeviceId))
                        }
                    }

                    is TrailsWebSocketServerMessage.Snapshot -> {
                        val device = when (val target = message.target) {
                            is TrailsWebSocketServerMessage.Snapshot.Target.Device -> runCatching { Uuid.parse(target.deviceId) }.getOrNull()?.let { devicesRepository.getDeviceById(it).firstOrNull() }
                            is TrailsWebSocketServerMessage.Snapshot.Target.Share -> runCatching { Uuid.parse(target.shareId) }.getOrNull()?.let { shareRepository.getShareById(it).firstOrNull()?.device }
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
    trailsServerRepositoryImpl: TrailsServerRepositoryImpl,
    keyValueRepository: KeyValueRepository,
    notificationRepository: NotificationRepository,
    userRepository: UserRepository,
    deviceRepository: DeviceRepository,
    database: TrailsDatabase,
    logger: Logger,
) : WebSocketClientBase(
    scope = scope,
    applicationRepository = applicationRepository,
    shareRepository = shareRepository,
    snapshotRepository = snapshotRepository,
    devicesRepository = devicesRepository,
    trailsServerRepositoryImpl = trailsServerRepositoryImpl,
    notificationRepository = notificationRepository,
    keyValueRepository = keyValueRepository,
    deviceRepository = deviceRepository,
    userRepository = userRepository,
    database = database,
    logger = logger,
)

private class ExternalServerWebSocketClient(
    scope: CoroutineScope,
    applicationRepository: ApplicationRepository,
    shareRepository: ShareRepository,
    snapshotRepository: SnapshotRepository,
    devicesRepository: DevicesRepository,
    deviceRepository: DeviceRepository,
    trailsServerRepositoryImpl: TrailsServerRepositoryImpl,
    userRepository: UserRepository,
    keyValueRepository: KeyValueRepository,
    notificationRepository: NotificationRepository,
    database: TrailsDatabase,
    logger: Logger,
) : WebSocketClientBase(
    scope = scope,
    applicationRepository = applicationRepository,
    shareRepository = shareRepository,
    snapshotRepository = snapshotRepository,
    devicesRepository = devicesRepository,
    trailsServerRepositoryImpl = trailsServerRepositoryImpl,
    userRepository = userRepository,
    deviceRepository = deviceRepository,
    keyValueRepository = keyValueRepository,
    notificationRepository = notificationRepository,
    database = database,
    logger = logger,
)
