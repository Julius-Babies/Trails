package es.jvbabi.trails.routes.webapp

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.webappSocket() {

    val userSubscriptionRepository by inject<UserSubscriptionRepository>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_WEBAPP_REALM) {
        webSocket {
            val user = call.principal<TrailsWebappPrincipal>()!!.user

            // One collector job per subscribed device flow.
            val deviceSubscriptions = mutableMapOf<Uuid, Job>()

            suspend fun sendDevices() {
                val devices = db.transaction {
                    user.devices
                        .filter { it.deletion == null }
                        .map { device -> WebAppSocketServerMessage.DevicesUpdate.Device.fromDevice(device) }
                }
                sendSerialized<WebAppSocketServerMessage>(WebAppSocketServerMessage.DevicesUpdate(devices = devices))
            }

            // Subscribe to a single device flow and re-send the device list on
            // every snapshot (location / battery change).
            fun subscribeToDevice(deviceId: Uuid) {
                if (deviceSubscriptions[deviceId]?.isActive == true) return
                deviceSubscriptions[deviceId] = launch {
                    deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                        .filter { it is DeviceSubscriptionMessage.Snapshot }
                        .onEach { sendDevices() }
                        .collect()
                }
            }

            // Send the current state right after connecting.
            sendDevices()

            // Subscribe to all currently owned devices.
            db.transaction { user.devices.filter { it.deletion == null }.map { it.id.value } }
                .forEach { subscribeToDevice(it) }

            // Re-send the full device list whenever a device of this user is
            // added, changed or removed, and keep the device subscriptions in sync.
            launch {
                userSubscriptionRepository.getFlowForUser(user.id.value)
                    .filter { it is UserSubscriptionMessage.DeviceUpdated || it is UserSubscriptionMessage.DeviceDeleted }
                    .onEach { message ->
                        when (message) {
                            is UserSubscriptionMessage.DeviceUpdated ->
                                subscribeToDevice(db.transaction { message.device.id.value })
                            is UserSubscriptionMessage.DeviceDeleted -> {
                                val deviceId = db.transaction { message.deletion.device.id.value }
                                deviceSubscriptions.remove(deviceId)?.cancel()
                            }
                            else -> {}
                        }
                        sendDevices()
                    }
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
            @SerialName("battery") val battery: Battery?
        ) {
            companion object {
                fun fromDevice(device: es.jvbabi.trails.database.Device): Device {
                    val latestSnapshot = DataSnapshot
                        .find { DataSnapshots.device eq device.id }
                        .orderBy(DataSnapshots.createdAt to SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()
                    return Device(
                        id = device.id.value,
                        manufacturer = device.manufacturer,
                        model = device.model,
                        friendlyName = device.friendlyName,
                        displayName = device.displayName,
                        battery = latestSnapshot?.let { snapshot ->
                            val level = snapshot.batteryLevel ?: return@let null
                            val charging = snapshot.batteryCharging ?: return@let null
                            Battery(
                                percentage = (level * 100).toInt(),
                                isCharging = charging
                            )
                        }
                    )
                }
            }

            @Serializable
            data class Battery(
                @SerialName("percentage") val percentage: Int,
                @SerialName("is_charging") val isCharging: Boolean
            )
        }
    }
}
