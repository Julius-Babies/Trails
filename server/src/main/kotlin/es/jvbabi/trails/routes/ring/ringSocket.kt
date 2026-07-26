package es.jvbabi.trails.routes.ring

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.User
import es.jvbabi.trails.routes.app.deviceRingInfo
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * Dedicated ring-state channel. Kept separate from the device-update socket and
 * from the command endpoints so ring state has one authoritative source: the
 * target device confirms start/stop (via the app socket), the server broadcasts
 * that through here, and every UI (app and web) reflects the confirmed state.
 *
 * Generic for both realms so the web (cookie) and, if ever needed, the app
 * (bearer) can consume it.
 */
fun Route.ringSocket() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM, TRAILS_WEBAPP_REALM) {
        webSocket {
            val userId = call.principal<TrailsAppUserPrincipal>()?.user?.id?.value
                ?: call.principal<TrailsWebappPrincipal>()?.user?.id?.value
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthenticated"))

            // Subscribe to confirmed ring-state changes first, so no update that
            // lands while we send the initial snapshot below is missed.
            val streamer = launch {
                userSubscriptionRepository.getFlowForUser(userId)
                    .filterIsInstance<UserSubscriptionMessage.RingState>()
                    .onEach { message ->
                        sendSerialized<RingSocketMessage>(
                            RingSocketMessage.RingState(
                                deviceId = message.deviceId.toString(),
                                isRinging = message.isRinging,
                                ringedBy = message.ringedByDeviceName,
                            )
                        )
                    }
                    .collect()
            }

            // Send the current ring state of every owned device so a UI that
            // (re)connects while a device is already ringing is up to date.
            val ownDeviceIds = db.transaction {
                User.findById(userId)?.devices?.filter { it.deletion == null }?.map { it.id.value } ?: emptyList()
            }
            ownDeviceIds.forEach { deviceId ->
                val ringedBy = deviceRingInfo[deviceId] ?: return@forEach
                sendSerialized<RingSocketMessage>(
                    RingSocketMessage.RingState(deviceId.toString(), isRinging = true, ringedBy = ringedBy)
                )
            }

            // Keep the connection open until the client disconnects; inbound
            // frames are ignored (this socket is server-push only).
            for (frame in incoming) { /* ignore */ }
            streamer.cancel()
        }
    }
}

@Serializable
sealed class RingSocketMessage {
    @SerialName("ring.state")
    @Serializable
    data class RingState(
        @SerialName("device_id") val deviceId: String,
        @SerialName("is_ringing") val isRinging: Boolean,
        @SerialName("ringed_by") val ringedBy: String,
    ) : RingSocketMessage()
}
