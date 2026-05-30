package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

class DeviceDeletion(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<DeviceDeletion>(DeviceDeletions)

    var deletedBy by Session referencedOn DeviceDeletions.deletedBy
    var device by Device referencedOn DeviceDeletions.deviceId
    var deletedAt by DeviceDeletions.deletedAt
}

object DeviceDeletions: UuidTable("device_deletion") {
    val deletedBy = reference("session", Sessions, onDelete = ReferenceOption.CASCADE)
    val deviceId = reference("device", Devices, onDelete = ReferenceOption.CASCADE)
    val deletedAt = timestamp("deleted_at").defaultExpression(CurrentTimestamp)
}