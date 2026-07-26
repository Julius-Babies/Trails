package es.jvbabi.trails.routes.active_share.item

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getActiveShare(): ActiveShare {
    val db by inject<DatabaseManager>()
    val activeShareId = parameters["activeShareId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("Active share not found")
    return db.transaction { ActiveShare.findById(activeShareId) } ?: throw EntityNotFoundException("Active share not found")
}
