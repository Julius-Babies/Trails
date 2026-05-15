package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

class Device(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Device>(Devices)

    var manufacturer by Devices.manufacturer
    var model by Devices.model
    var friendlyName by Devices.friendlyName
    var displayName by Devices.displayName
    var type by Devices.type
    var owner by User referencedOn Devices.owner
    var createdAt by Devices.createdAt
}

object Devices : UuidTable("devices") {
    val manufacturer = varchar("manufacturer", 255)
    val model = varchar("model", 255)
    val friendlyName = varchar("friendly_name", 255)
    val displayName = varchar("display_name", 255)
    val type = enumerationByName<DeviceType>("type", 16)
    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

enum class DeviceType {
    Phone,
}
