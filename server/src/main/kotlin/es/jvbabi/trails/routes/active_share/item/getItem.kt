package es.jvbabi.trails.routes.active_share.item

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.mapper.toApi
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.activeShareItem() {
    val db by inject<DatabaseManager>()
    get {
        val activeShare = call.getActiveShare()
        call.respond(db.transaction { activeShare.toApi() })
    }
}

suspend fun ApplicationCall.getActiveShare(): ActiveShare {
    val db by inject<DatabaseManager>()
    val activeShareId = parameters["activeShareId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("Active share not found")
    return db.transaction { ActiveShare.findById(activeShareId) } ?: throw EntityNotFoundException("Active share not found")
}
