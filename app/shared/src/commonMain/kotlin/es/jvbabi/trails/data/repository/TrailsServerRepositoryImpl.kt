package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.api.v1.me.RegisterUserShareRequest
import es.jvbabi.trails.api.v1.share.RedeemShareResponse
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.ConnectionEvent
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbConnectionEvent
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbUser
import es.jvbabi.trails.data.remote.ApiException
import es.jvbabi.trails.data.remote.TrailsApi
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.shared.dto.DeviceResponse
import es.jvbabi.trails.shared.dto.MeResponse
import es.jvbabi.trails.shared.dto.SessionHealthResponse
import es.jvbabi.trails.shared.dto.websocket.PingSource
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketAppMessage
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.*
import io.ktor.client.call.*
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
    private val trailsApi: TrailsApi,
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

    override val ringStates: StateFlow<Map<Uuid, RingDeviceState>>
        field = MutableStateFlow<Map<Uuid, RingDeviceState>>(emptyMap())

    val pendingPingResults = mutableMapOf<Uuid, CompletableDeferred<PingResult>>()

    fun updateRingState(deviceId: Uuid, state: RingDeviceState) {
        ringStates.value = ringStates.value + (deviceId to state)
    }

    fun removeRingState(deviceId: Uuid) {
        ringStates.value = ringStates.value - deviceId
    }

    fun setDeviceDeletedState(state: IsDeviceDeletedState) {
        isDeviceDeletedState.value = state
    }

    override suspend fun resetDeviceDeletedState() {
        val deletedState = isDeviceDeletedState.value
        if (deletedState is IsDeviceDeletedState.Deleted) {
            database.deviceDao.deleteDevicesByIds(listOf(deletedState.thisDevice.id))
            keyValueRepository.delete(Key.ThisDeviceId)
            keyValueRepository.delete(Key.UserId)
            keyValueRepository.delete(Key.Host)
            keyValueRepository.delete(Key.Token)
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

    /**
     * Wartet bis zu [delayMs] ms, wacht aber sofort auf, sobald die App (neu) in den
     * Vordergrund kommt. Gibt true zurück, wenn durch den Vordergrund-Wechsel geweckt
     * wurde – dann soll unmittelbar ein neuer Verbindungsversuch erfolgen und der
     * Backoff zurückgesetzt werden.
     */
    private suspend fun delayOrUntilForeground(delayMs: Long): Boolean {
        return withTimeoutOrNull(delayMs.milliseconds) {
            applicationRepository.getApplicationForegroundState()
                .dropWhile { it }   // aktuellen Vordergrund-Zustand überspringen
                .first { it }       // auf Wechsel nach Vordergrund warten
            true
        } ?: false
    }

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

                    val token = keyValueRepository.get(Key.Token).first()
                        ?: throw IllegalStateException("Token not set")
                    val currentDeviceId = keyValueRepository.get(Key.ThisDeviceId).first()
                        ?: throw IllegalStateException("Current device ID not set")
                    val device = runCatching { devicesRepository.getDeviceById(currentDeviceId).first() }
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

                // Anzahl Fehlversuche, nach denen der Aufrufer nicht länger blockiert wird.
                // Die Schleife gibt NICHT auf, sondern versucht im Hintergrund weiter zu
                // verbinden (Tracking-App muss dauerhaft reconnecten).
                val retriesBeforeUnblockingCaller = 30
                if (!wasConnected) {
                    if (currentRetry >= retriesBeforeUnblockingCaller && !deferred.isCompleted) {
                        deferred.complete(false)
                    }
                    val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                        1_000L
                    } else {
                        // 1L shl bei zu großem Exponenten vermeiden -> Exponent deckeln.
                        minOf(30_000L, 5_000L * (1L shl minOf(currentRetry, 6)))
                    }
                    // Kommt die App in den Vordergrund, sofort erneut versuchen und Backoff resetten.
                    if (delayOrUntilForeground(delayMs)) currentRetry = 0 else currentRetry++
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
        return keyValueRepository.get(Key.Host)
            .map {
                if (it == null) null
                else URLBuilder(it.let {
                    if (it.startsWith("https://")) it
                    else "https://$it"
                })
            }
    }

    override fun getToken(): Flow<String?> {
        return keyValueRepository.get(Key.Token)
    }

    override fun getUserId(): Flow<Uuid?> = keyValueRepository.get(Key.UserId)

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
                val thisDeviceId = keyValueRepository.get(Key.ThisDeviceId).first() ?: return SessionHealthState.NoSessionExpected
                val thisDevice = devicesRepository.getDeviceById(thisDeviceId).firstOrNull() ?: return SessionHealthState.NoSessionExpected
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
                keyValueRepository.delete(Key.Token)
                keyValueRepository.delete(Key.UserId)
                keyValueRepository.delete(Key.ThisDeviceId)
                keyValueRepository.delete(Key.Host)

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

        keyValueRepository.set(Key.UserId, Uuid.parse(body.id))
        keyValueRepository.set(Key.ThisDeviceId, Uuid.parse(body.thisDeviceId))

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

    override suspend fun requestPing(device: Device): PingResult {
        val deferred = CompletableDeferred<PingResult>()
        pendingPingResults[device.id] = deferred
        val session = websocketSession
        if (session == null || !session.isActive) return PingResult.Error("WebSocket not connected")
        session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.DevicePing(device.id.toString()))
        val result = withTimeoutOrNull(10.seconds) { deferred.await() }
        pendingPingResults.remove(device.id)
        return result ?: PingResult.Timeout
    }

    override fun requestRing(device: Device) {
        scope.launch {
            val session = websocketSession ?: return@launch
            session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.DeviceRing(device.id.toString()))
        }
    }

    override fun requestStopRing(device: Device) {
        scope.launch {
            val session = websocketSession ?: return@launch
            session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.DeviceRingStop(device.id.toString()))
        }
    }

    override suspend fun useShareLink(hostname: String, id: String): UseShareLinkResult {
        val redeemUrl = URLBuilder("https://$hostname").apply {
            appendPathSegments("api", "v1", "share", id, "redeem")
        }.buildString()

        val response = httpClient.post(redeemUrl)
        if (response.status == HttpStatusCode.NotFound) return UseShareLinkResult.NotExisting
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Forbidden) {
            Logger.e(NetworkRequestUnsuccessfulException(response)) { "Error using share link" }
            return UseShareLinkResult.Error("Error using share link: ${response.status}")
        }

        val activeShareId = when (val body = response.body<RedeemShareResponse>()) {
            RedeemShareResponse.ShareLocked -> return UseShareLinkResult.Used
            is RedeemShareResponse.Success -> body.activeShareId
        }

        return try {
            resolveAndStoreActiveShare(hostname, activeShareId)
            // Back up the share to our own account if we are signed in.
            registerActiveShareWithAccount(originHomeserver = hostname, activeShareId = activeShareId)
            UseShareLinkResult.Success
        } catch (e: ApiException) {
            if (e.statusCode == HttpStatusCode.NotFound.value) {
                UseShareLinkResult.NotExisting
            } else {
                Logger.e(e) { "Error resolving share entities" }
                UseShareLinkResult.Error("Error using share link: ${e.statusCode}")
            }
        }
    }

    /**
     * Resolves an active share on [hostname] via the chain ActiveShare -> Share -> Device ->
     * Owner and stores the user, device and active share locally.
     */
    private suspend fun resolveAndStoreActiveShare(hostname: String, activeShareId: Uuid) {
        val activeShare = trailsApi.getActiveShare(hostname, activeShareId)
        val share = trailsApi.getShare(hostname, activeShare.shareId)
        val device = trailsApi.getDevice(hostname, share.deviceId)
        val owner = trailsApi.getUser(hostname, device.ownerId)

        database.userDao.upsert(DbUser(
            id = owner.id,
            homeserver = hostname,
            username = owner.username,
        ))

        database.deviceDao.upsertDevices(listOf(
            DbDevice(
                id = device.id,
                manufacturer = device.manufacturer,
                model = device.model,
                friendlyName = device.friendlyName,
                displayName = device.displayName,
                ownerId = owner.id,
            )
        ))

        val localDevice = devicesRepository.getDeviceById(device.id).first()
            ?: throw IllegalStateException("Device not found after using share link")
        if (!devicesRepository.hasDeviceImage(localDevice).first()) {
            fetchDeviceImageForDevice(localDevice)
        }

        database.activeShareDao.upsert(DbActiveShare(
            id = activeShareId,
            deviceId = device.id,
        ))
    }

    /**
     * Registers a redeemed active share with the account on our homeserver so it can be
     * restored on app start. Best-effort: if it fails (or we are not signed in), the local
     * redeem still remains valid.
     */
    private suspend fun registerActiveShareWithAccount(originHomeserver: String, activeShareId: Uuid) {
        val token = getToken().first() ?: return
        val accountHost = getBaseUrl().first()?.host ?: return
        runCatching {
            trailsApi.registerUserShare(
                host = accountHost,
                token = token,
                request = RegisterUserShareRequest(shareId = activeShareId, homeserver = originHomeserver),
            )
        }.onFailure { Logger.w(it) { "Failed to register share with account" } }
    }

    override suspend fun syncAccountShares() {
        val token = getToken().first() ?: return
        val accountHost = getBaseUrl().first()?.host ?: return

        val shares = runCatching { trailsApi.getUserShares(accountHost, token) }
            .getOrElse {
                Logger.w(it) { "Failed to download account shares" }
                return
            }

        shares.forEach { entry ->
            runCatching { resolveAndStoreActiveShare(entry.homeserver, entry.shareId) }
                .onFailure { Logger.w(it) { "Failed to restore share ${entry.shareId}" } }
        }
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

            if (!wasConnected) {
                // Nie endgültig aufgeben, weiter mit gedeckeltem Backoff versuchen.
                val delayMs = if (applicationRepository.getApplicationForegroundState().first()) {
                    1_000L
                } else {
                    minOf(30_000L, 5_000L * (1L shl minOf(currentRetry, 6)))
                }
                // Kommt die App in den Vordergrund, sofort erneut versuchen und Backoff resetten.
                if (delayOrUntilForeground(delayMs)) currentRetry = 0 else currentRetry++
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
                        val body = when (message.pingedBySource) {
                            PingSource.BROWSER -> "Ein Browser hat dieses Gerät gefunden!"
                            PingSource.DEVICE -> "Dein ${message.pingedByDeviceName} hat dieses Gerät gefunden!"
                        }
                        val notificationSent = notificationRepository.sendNotification(
                            channelId = NotificationRepository.PING_CHANNEL_ID,
                            title = "Gerät gefunden",
                            body = body,
                            notificationId = message.pingedByDeviceName.hashCode()
                        )
                        session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.Pong(notificationSent))
                    }

                    is TrailsWebSocketServerMessage.Ring -> {
                        deviceRepository.startRinging(
                            causedByDeviceName = message.ringedByDeviceName,
                            onStop = { scope.launch { session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.RingStop) } }
                        )
                        session.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.RingStart)
                    }

                    is TrailsWebSocketServerMessage.RingStop -> {
                        deviceRepository.stopRinging()
                    }

                    is TrailsWebSocketServerMessage.RingState -> {
                        val deviceId = runCatching { Uuid.parse(message.deviceId) }.getOrNull() ?: continue
                        if (message.isRinging) {
                            trailsServerRepositoryImpl.updateRingState(deviceId, RingDeviceState(isRinging = true, ringedByDeviceName = message.ringedByDeviceName))
                        } else {
                            trailsServerRepositoryImpl.removeRingState(deviceId)
                        }
                    }

                    is TrailsWebSocketServerMessage.PingResult -> {
                        val targetDeviceId = runCatching { Uuid.parse(message.deviceId) }.getOrNull() ?: continue
                        val deferred = trailsServerRepositoryImpl.pendingPingResults[targetDeviceId] ?: continue
                        if (message.success) {
                            deferred.complete(PingResult.Pinged(message.hasDeliveredNotification))
                        } else {
                            deferred.complete(PingResult.Error(message.errorMessage ?: "Unknown error"))
                        }
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
                        val thisDeviceId = keyValueRepository.get(Key.ThisDeviceId).firstOrNull()
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
