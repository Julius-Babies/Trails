package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

class ActiveShare(id: EntityID<Uuid>): UuidEntity(id) {
    companion object : UuidEntityClass<ActiveShare>(ActiveShares)

    var share by Share referencedOn ActiveShares.share
    var createdAt by ActiveShares.createdAt
}

object ActiveShares: UuidTable("active_shares") {
    val share = reference("share", Shares, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
