package es.jvbabi.trails.routes.share.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.DbShare
import es.jvbabi.trails.database.mapper.toApi
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.shareItem() {
    val db by inject<DatabaseManager>()
    get {
        val share = call.getShare()
        call.respond(db.transaction { share.toApi() })
    }
}

suspend fun ApplicationCall.getShare(): DbShare {
    val db by inject<DatabaseManager>()
    val shareId = parameters["shareId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("Share not found")
    return db.transaction { DbShare.findById(shareId) } ?: throw EntityNotFoundException("Share not found")
}