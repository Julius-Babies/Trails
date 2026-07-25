package es.jvbabi.trails.routes.webapp

import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.webappSocket() {

    val userSubscriptionRepository by inject<UserSubscriptionRepository>()
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_WEBAPP_REALM) {
        webSocket {
            val user = call.principal<TrailsWebappPrincipal>()!!.user

            suspend fun sendDevices() {
                val devices = db.transaction {
                    user.devices
                        .filter { it.deletion == null }
                        .map { device -> WebAppSocketServerMessage.DevicesUpdate.Device.fromDevice(device) }
                }
                sendSerialized<WebAppSocketServerMessage>(WebAppSocketServerMessage.DevicesUpdate(devices = devices))
            }

            // Send the current state right after connecting.
            sendDevices()

            // Re-send the full device list whenever a device of this user is
            // added, changed or removed.
            launch {
                userSubscriptionRepository.getFlowForUser(user.id.value)
                    .filter { it is UserSubscriptionMessage.DeviceUpdated || it is UserSubscriptionMessage.DeviceDeleted }
                    .onEach { sendDevices() }
                    .collect()
            }

            // Keep the connection open until the client disconnects.
            for (frame in incoming) {
                // The webapp socket is server-push only; ignore inbound frames.
            }
        }
    }
}

@Serializable
sealed class WebAppSocketServerMessage {
    @SerialName("devices.update")
    @Serializable
    data class DevicesUpdate(
        @SerialName("devices") val devices: List<Device>
    ): WebAppSocketServerMessage() {
        @Serializable
        data class Device(
            @SerialName("id") val id: Uuid,
            @SerialName("manufacturer") val manufacturer: String,
            @SerialName("model") val model: String,
            @SerialName("friendly_name") val friendlyName: String,
            @SerialName("display_name") val displayName: String,
        ) {
            companion object {
                fun fromDevice(device: es.jvbabi.trails.database.Device): Device {
                    return Device(
                        id = device.id.value,
                        manufacturer = device.manufacturer,
                        model = device.model,
                        friendlyName = device.friendlyName,
                        displayName = device.displayName,
                    )
                }
            }
        }
    }
}