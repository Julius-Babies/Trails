package es.jvbabi.trails.routes.me

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.me() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        get {
            val auth = call.principal<TrailsAppUserPrincipal>()!!

            db.transaction {
                MeResponse(
                    id = auth.user.id.value.toString(),
                    username = auth.user.username,
                    thisDeviceId = auth.device.id.value.toString(),
                )
            }.let {
                call.respond(it)
            }
        }
    }
}

@Serializable
data class MeResponse(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("this_device_id") val thisDeviceId: String,
)