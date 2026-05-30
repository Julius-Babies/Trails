package es.jvbabi.trails.routes.me

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.shared.dto.MeResponse
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.me() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        get {
            val auth = call.principal<TrailsAppUserPrincipal>()!!
            auth.requireValidSession()

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