package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import io.ktor.server.application.ApplicationCall
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getShare(): Share {
    val db by inject<DatabaseManager>()
    val shareId = parameters["shareId"]?.let(Uuid::parseOrNull) ?: throw IllegalArgumentException("Share ID not found")
    val share = db.transaction { Share.findById(shareId) } ?: throw IllegalArgumentException("Share not found")
    return share
}