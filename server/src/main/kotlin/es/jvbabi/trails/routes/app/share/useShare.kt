package es.jvbabi.trails.routes.app.share

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.shared.dto.DeviceResponse
import es.jvbabi.trails.shared.dto.UseShareLinkRequest
import es.jvbabi.trails.shared.dto.UseShareLinkResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.useShare() {
    val db by inject<DatabaseManager>()
    post {
        val request = call.receive<UseShareLinkRequest>()

        val share = db.transaction {
            Share.findById(Uuid.parse(request.id))
        }

        if (share == null) {
            call.respond(
                status = HttpStatusCode.NotFound,
                message = "ERR_SHARE_NOT_FOUND"
            )
            return@post
        }

        if (share.isLocked) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "ERR_SHARE_LOCKED"
            )
        }

        val activeShare = db.transaction {
            val activeShare = ActiveShare.new {
                this.share = share
            }
            if (!share.allowMultiuse) share.isLocked = true
            activeShare
        }

        db.transaction {
            UseShareLinkResponse(
                shareId = activeShare.id.value.toString(),
                user = UseShareLinkResponse.User(
                    id = share.device.owner.id.value.toString(),
                    username = share.device.owner.username,
                ),
                device = DeviceResponse(
                    id = share.device.id.value.toString(),
                    manufacturer = share.device.manufacturer,
                    model = share.device.model,
                    friendlyName = share.device.friendlyName,
                    displayName = share.device.displayName,
                ),
            )
        }.let { call.respond(it) }
    }
}
