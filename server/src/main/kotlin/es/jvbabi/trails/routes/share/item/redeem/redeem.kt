package es.jvbabi.trails.routes.share.item.redeem

import es.jvbabi.trails.api.v1.share.RedeemShareResponse
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.routes.share.item.getShare
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.redeemShare() {
    val db by inject<DatabaseManager>()
    val userSubscriptionRepository by inject<UserSubscriptionRepository>()

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

        // Tell the emitting user so their open webapp sockets pick up the new
        // redemption (and a now-locked single-use share) right away.
        val (shareId, ownerId) = db.transaction { share.id.value to share.device.owner.id.value }
        userSubscriptionRepository.getFlowForUser(ownerId)
            .emit(UserSubscriptionMessage.EmittedSharesChanged(shareId))

        call.respond<RedeemShareResponse>(
            message = RedeemShareResponse.Success(
                activeShareId = activeShare.id.value
            ),
            status = HttpStatusCode.OK,
        )
    }
}
