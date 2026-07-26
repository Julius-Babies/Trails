package es.jvbabi.trails.routes.user.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.User
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getUser(): User {
    val db by inject<DatabaseManager>()
    val userId = parameters["userId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("User not found")
    return db.transaction { User.findById(userId) } ?: throw EntityNotFoundException("User not found")
}
