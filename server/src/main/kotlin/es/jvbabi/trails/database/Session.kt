package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid


class Session(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Session>(Sessions)

    var device by Device referencedOn Sessions.device
    var tokenHash by Sessions.tokenHash
    var createdAt by Sessions.createdAt
    var invalidatedAt by Sessions.invalidatedAt
}

object Sessions : UuidTable("sessions") {
    val device = reference("device", Devices, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val invalidatedAt = timestamp("invalidated_at").nullable().default(null)
}