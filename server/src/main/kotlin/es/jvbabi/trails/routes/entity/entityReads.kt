package es.jvbabi.trails.routes.entity

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.User
import es.jvbabi.trails.database.mapper.toApi
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * Öffentliche, lesende Entity-Endpunkte (generische REST-API). Der Zugriff erfolgt
 * per unratbarer UUID (Capability-Muster) – konsistent damit, dass Redeem und die
 * externen WebSocket-Verbindungen ebenfalls ohne App-Session funktionieren.
 */

private fun RoutingContext.uuidParam(name: String): Uuid? =
    call.parameters[name]?.let(Uuid::parseOrNull)

fun Route.getDeviceById() {
    val db by inject<DatabaseManager>()
    get("/{deviceId}") {
        val id = uuidParam("deviceId") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val dto = db.transaction { Device.findById(id)?.toApi() }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(dto)
    }
}

fun Route.getShareById() {
    val db by inject<DatabaseManager>()
    get("/{shareId}") {
        val id = uuidParam("shareId") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val dto = db.transaction { Share.findById(id)?.toApi() }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(dto)
    }
}

fun Route.getActiveShareById() {
    val db by inject<DatabaseManager>()
    get("/{activeShareId}") {
        val id = uuidParam("activeShareId") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val dto = db.transaction { ActiveShare.findById(id)?.toApi() }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(dto)
    }
}

fun Route.getUserById() {
    val db by inject<DatabaseManager>()
    get("/{userId}") {
        val id = uuidParam("userId") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val dto = db.transaction { User.findById(id)?.toApi() }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(dto)
    }
}
