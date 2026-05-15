package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
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

class TrailsServerRepositoryImpl(
    private val httpClient: HttpClient,
    private val keyValueRepository: KeyValueRepository,
    private val locationRepository: LocationRepository,
): TrailsServerRepository {

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

            logger.i { "Connecting to WS at ${url.buildString()}" }

            try {
                websocketSession = httpClient.webSocketSession(
                    urlString = url.buildString()
                ) {
                    bearerAuth(token)
                }

                isConnected.value = true

                val locationUpdater = scope.launch {
                    locationRepository.getCurrentLocation()
                        .filterNotNull()
                        .distinctUntilChangedBy { location -> location.copy(time = Instant.DISTANT_PAST.toLocalDateTime(TimeZone.currentSystemDefault())) }
                        .takeWhile { isConnected.value }
                        .collectLatest {
                            val websocketSession = websocketSession ?: return@collectLatest
                            logger.i { "Sending location update: $it" }
                            websocketSession.sendSerialized<TrailsWebSocketAppMessage>(TrailsWebSocketAppMessage.DataSnapshot(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                bearing = it.bearing,
                                bearingAccuracy = it.bearingAccuracy,
                                locationAccuracy = it.locationAccuracy,
                                batteryLevel = it.batteryLevel,
                                time = it.time.toInstant(TimeZone.currentSystemDefault()).epochSeconds,
                            ))
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
                Logger.e (e) { "Error connecting to WS: ${e.message}" }
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
}

@Serializable
private sealed class TrailsWebSocketAppMessage {
    @SerialName("data_snapshot")
    @Serializable
    data class DataSnapshot(
        @SerialName("latitude") val latitude: Double,
        @SerialName("longitude") val longitude: Double,
        @SerialName("bearing") val bearing: Float,
        @SerialName("bearing_accuracy") val bearingAccuracy: Float? = null,
        @SerialName("location_accuracy") val locationAccuracy: Float,
        @SerialName("battery_level") val batteryLevel: Float? = null,
        @SerialName("time") val time: Long,
    ) : TrailsWebSocketAppMessage()
}
