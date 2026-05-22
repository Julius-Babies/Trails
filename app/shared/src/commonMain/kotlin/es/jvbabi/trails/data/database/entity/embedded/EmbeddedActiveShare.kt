package es.jvbabi.trails.data.database.entity.embedded

import androidx.room.Embedded
import androidx.room.Relation
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.domain.model.ActiveShare

data class EmbeddedActiveShare(
    @Embedded val share: DbActiveShare,
    @Relation(
        parentColumn = "device_id",
        entityColumn = "id",
        entity = DbDevice::class
    ) val device: EmbeddedDevice
) {
    fun toModel() = ActiveShare(
        id = share.id,
        device = device.toModel(),
    )
}
