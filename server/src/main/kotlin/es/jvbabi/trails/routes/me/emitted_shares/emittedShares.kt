package es.jvbabi.trails.routes.me.emitted_shares

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.api.v1.me.EmittedShareResponse
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.ActiveShares
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.Shares
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.ktor.ext.inject

fun Route.getEmittedShares() {
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        get {
            val principal = call.principal<TrailsAppUserPrincipal>()!!
            principal.requireValidSession()

            val shares = db.transaction {
                Share.wrapRows(
                    Shares
                        .innerJoin(Devices)
                        .select(Shares.columns)
                        .where { Devices.owner eq principal.user.id }
                ).map { share ->
                    EmittedShareResponse(
                        id = share.id.value,
                        deviceId = share.device.id.value,
                        shareName = share.shareName,
                        locationHistorySeconds = share.locationHistorySeconds,
                        shareBatteryState = share.shareBatteryState,
                        allowMultiuse = share.allowMultiuse,
                        isLocked = share.isLocked,
                        createdAt = share.createdAt.epochSeconds,
                        redemptionCount = ActiveShare.count(ActiveShares.share eq share.id),
                    )
                }
            }

            call.respond(shares)
        }
    }
}
