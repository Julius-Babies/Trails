package es.jvbabi.trails.routes.me

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.Devices
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject

fun Route.devices() {
    val db by inject<DatabaseManager>()
    authenticate(TRAILS_USER_REALM) {
        get {
            val auth = call.principal<TrailsAppUserPrincipal>()!!

            db.transaction {
                Device
                    .find { Devices.owner eq auth.user.id }
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
    }
}

@Serializable
data class DeviceResponse(
    @SerialName("id") val id: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("friendly_name") val friendlyName: String,
    @SerialName("display_name") val displayName: String,
)