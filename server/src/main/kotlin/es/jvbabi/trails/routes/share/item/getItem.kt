package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getShare(): Share {
    val db by inject<DatabaseManager>()
    val shareId = parameters["shareId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("Share not found")
    return db.transaction { Share.findById(shareId) } ?: throw EntityNotFoundException("Share not found")
}