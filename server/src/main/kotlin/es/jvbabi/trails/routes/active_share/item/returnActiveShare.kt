package es.jvbabi.trails.routes.active_share.item

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * `POST /active-shares/{activeShareId}/return` — gives back a redeemed share by
 * deleting its [ActiveShare]. Capability-based and unauthenticated: holding the
 * active-share id (an unguessable UUID) is the permission, mirroring the public
 * redeem/snapshot endpoints. This is the origin-homeserver half of a return; the
 * account server separately drops its saved reference.
 *
 * Deleting the redemption deliberately does **not** touch [share.isLocked] — a
 * spent single-use share stays locked, so the link cannot be redeemed again.
 *
 * A `POST` (not `DELETE`) so a cross-homeserver browser call stays a CORS
 * "simple request" and needs no preflight; the response is CORS-open like the
 * snapshot endpoint.
 */
fun Route.returnActiveShare() {
    val db by inject<DatabaseManager>()

    post {
        // Cross-homeserver federation runs in the browser, so allow any origin.
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")

        val activeShareId = call.parameters["activeShareId"]?.let(Uuid::parseOrNull)
            ?: return@post call.respond(HttpStatusCode.NotFound)

        // Idempotent: an already-returned (missing) share is still a success.
        db.transaction { ActiveShare.findById(activeShareId)?.delete() }

        call.respond(HttpStatusCode.NoContent)
    }
}
