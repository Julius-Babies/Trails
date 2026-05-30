package es.jvbabi.trails.routes.app.session_healthcheck

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.shared.dto.SessionHealthResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.sessionHealthCheck() {

    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM, optional = true) {
        get {
            val principal = call.principal<TrailsAppUserPrincipal>()!!
            val device = principal.device

            val deviceDeletion = db.transaction { device.deletion }
            if (deviceDeletion != null) {
                call.respond<SessionHealthResponse>(SessionHealthResponse.DeviceDeleted(
                    deletedByDeviceName = db.transaction { deviceDeletion.deletedBy.device.displayName }
                ))
                return@get
            }

            call.respond<SessionHealthResponse>(SessionHealthResponse.Valid)
        }
    }
}