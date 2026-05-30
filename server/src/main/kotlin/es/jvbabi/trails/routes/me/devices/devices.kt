package es.jvbabi.trails.routes.me.devices

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.DeviceDeletion
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.shared.dto.DeviceResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

val deviceKey = AttributeKey<Device>("device")

fun Route.devices() {
    val db by inject<DatabaseManager>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()

    authenticate(TRAILS_USER_REALM) {
        get {
            val auth = call.principal<TrailsAppUserPrincipal>()!!
            auth.requireValidSession()

            db.transaction {
                Device
                    .find { (Devices.owner eq auth.user.id) and (Devices.deletion eq null) }
                    .toList()
                    .map { device ->
                        DeviceResponse(
                            id = device.id.value.toString(),
                            manufacturer = device.manufacturer,
                            model = device.model,
                            friendlyName = device.friendlyName,
                            displayName = device.displayName,
                        )
                    }
            }.let {
                call.respond(it)
            }
        }

        route("/{deviceId}") {
            createRouteScopedPlugin("Devices") {
                route!!.plugin(Authentication)
                on(AuthenticationChecked) { call ->
                    val deviceId = call.parameters["deviceId"]!!.let(Uuid::parse)
                    val device = db.transaction { Device.findById(deviceId) }
                    val principal = call.principal<TrailsAppUserPrincipal>()!!
                    principal.requireValidSession()
                    requireNotNull(device) { "Device not found" }
                    require(db.transaction {
                        device.owner.id.value == principal.user.id.value
                    }) { "Device does not belong to user" }

                    call.attributes[deviceKey] = device
                }
            }.let { install(it) }

            delete {
                val device = call.attributes[deviceKey]
                val principal = call.principal<TrailsAppUserPrincipal>()!!
                principal.requireValidSession()
                val deletion = db.transaction {
                    val deletion = DeviceDeletion.new {
                        this.device = device
                        this.deletedBy = principal.session
                    }

                    device.deletion = deletion
                    deletion
                }

                val flow = deviceSubscriptionRepository.getFlowForDeviceSubscription(device.id.value)
                flow.emit(DeviceSubscriptionMessage.Deleted(deletion))

                call.respond(buildMap { put("success", true) })
            }
        }
    }
}