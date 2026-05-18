package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.Uuid

class Share(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Share>(Shares)

    var device by Device referencedOn Shares.device
    var locationHistorySeconds by Shares.locationHistorySeconds
    var shareBatteryState by Shares.shareBatteryState
    var shareName by Shares.shareName
    var isLocked by Shares.isLocked
    var allowMultiuse by Shares.allowMultiuse
    var createdAt by Shares.createdAt
}

object Shares: UuidTable("shares") {
    val device = reference("device", Devices, onDelete = ReferenceOption.CASCADE)
    val locationHistorySeconds = integer("location_history_seconds")
    val shareBatteryState = bool("share_battery_state")
    val shareName = varchar("share_name", 255)
    val isLocked = bool("is_locked")
    val allowMultiuse = bool("allow_multiuse")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}