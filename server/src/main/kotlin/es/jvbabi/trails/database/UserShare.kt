package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

/**
 * A share (redeemed active share) that a signed-in user has saved to their own account.
 * [shareId] is the active-share id on [homeserver] — the server the share lives on (which
 * may be a foreign server) — so it can be resolved again on app start.
 */
class UserShare(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<UserShare>(UserShares)

    var user by DbUser referencedOn UserShares.user
    var shareId by UserShares.shareId
    var homeserver by UserShares.homeserver
    var createdAt by UserShares.createdAt
}

object UserShares : UuidTable("user_shares") {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val shareId = uuid("share_id")
    val homeserver = varchar("homeserver", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
