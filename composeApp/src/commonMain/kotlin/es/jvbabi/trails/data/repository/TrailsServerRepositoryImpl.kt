package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class TrailsServerRepositoryImpl(
    private val httpClient: HttpClient,
    private val keyValueRepository: KeyValueRepository,
): TrailsServerRepository {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag("TrailsServerRepositoryImpl")

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
                httpClient.webSocket(
                    urlString = url.buildString(),
                    request = {
                        bearerAuth(token)
                    }
                ) {
                    isConnected.value = true

                    sendSerialized("Hello, server!")
                    for (frame in incoming) {
                        if (frame is Frame.Text) println(frame.readText())
                    }

                    isConnected.value = false
                }
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