package es.jvbabi.trails.routes.share

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.v1.share.CreateShareRequest
import es.jvbabi.trails.api.v1.share.CreateShareResponse
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.Shares
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.ktor.ext.inject

fun Route.createShare() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        post {
            val principal = call.principal<TrailsAppUserPrincipal>()!!
            principal.requireValidSession()

            val request = call.receive<CreateShareRequest>()

            val doesShareWithSameNameExist = db.transaction {
                val shareWithSameNameForSameUser = Shares
                    .leftJoin(Devices)
                    .select(Shares.id)
                    .where { Devices.owner eq principal.user.id }
                    .andWhere { Shares.shareName eq request.shareName }
                return@transaction shareWithSameNameForSameUser.count() > 0
            }

            if (doesShareWithSameNameExist) {
                call.respond<CreateShareResponse>(
                    message = CreateShareResponse.ShareNameAlreadyExists,
                    status = HttpStatusCode.Conflict
                )
                return@post
            }

            val createdShareId = db.transaction {
                Share.new {
                    this.device = principal.device
                    this.shareName = request.shareName
                    this.locationHistorySeconds = request.locationHistorySeconds
                    this.allowMultiuse = request.allowMultiuse
                    this.shareBatteryState = request.shareBattery
                    this.isLocked = false
                }.id.value
            }

            call.respond<CreateShareResponse>(
                message = CreateShareResponse.ShareCreated(createdShareId),
                status = HttpStatusCode.Created
            )
        }
    }
}
