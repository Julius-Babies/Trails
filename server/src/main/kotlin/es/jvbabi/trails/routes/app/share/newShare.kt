package es.jvbabi.trails.routes.app.share

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Share
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.newShare() {

    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        post {
            val auth = call.principal<TrailsAppUserPrincipal>()!!
            val request = call.receive<NewShareRequest>()

            val share = db.transaction {
                Share.new {
                    this.device = auth.device
                    this.shareName = request.shareName
                    this.locationHistorySeconds = request.historyDurationSeconds
                    this.shareBatteryState = request.batteryState
                    this.isLocked = false
                    this.allowMultiuse = request.allowMultiuse
                }
            }

            call.respond(ShareResponse(shareId = share.id.value.toString()))
        }
    }
}

@Serializable
data class NewShareRequest(
    @SerialName("history_duration_seconds") val historyDurationSeconds: Int,
    @SerialName("battery_state") val batteryState: Boolean,
    @SerialName("share_name") val shareName: String,
    @SerialName("allow_multiuse") val allowMultiuse: Boolean,
)

@Serializable
data class ShareResponse(
    @SerialName("share_id") val shareId: String,
)