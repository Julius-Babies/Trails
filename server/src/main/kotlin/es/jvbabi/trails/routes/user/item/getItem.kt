package es.jvbabi.trails.routes.user.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.DbUser
import es.jvbabi.trails.database.mapper.toApi
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.userItem() {
    val db by inject<DatabaseManager>()
    get {
        val user = call.getUser()
        call.respond(db.transaction { user.toApi() })
    }
}

suspend fun ApplicationCall.getUser(): DbUser {
    val db by inject<DatabaseManager>()
    val userId = parameters["userId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("User not found")
    return db.transaction { DbUser.findById(userId) } ?: throw EntityNotFoundException("User not found")
}
