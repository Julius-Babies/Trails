package es.jvbabi.trails.routes.share.item.redeem

import es.jvbabi.trails.api.v1.share.RedeemShareResponse
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.routes.share.item.getShare
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.redeemShare() {
    val db by inject<DatabaseManager>()

    post {
        val share = call.getShare()

        if (share.isLocked) return@post call.respond<RedeemShareResponse>(
            message = RedeemShareResponse.ShareLocked,
            status = HttpStatusCode.Forbidden,
        )

        val activeShare = db.transaction {
            val activeShare = ActiveShare.new {
                this.share = share
            }

            if (!share.allowMultiuse) share.isLocked = true
            activeShare
        }

        call.respond<RedeemShareResponse>(
            message = RedeemShareResponse.Success(
                activeShareId = activeShare.id.value
            ),
            status = HttpStatusCode.OK,
        )
    }
}