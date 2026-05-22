package es.jvbabi.trails.data.database.entity.embedded

import androidx.room.Embedded
import androidx.room.Relation
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbUser
import es.jvbabi.trails.domain.model.Device

data class EmbeddedDevice(
    @Embedded val device: DbDevice,
    @Relation(
        parentColumn = "owner_id",
        entityColumn = "id",
        entity = DbUser::class,
    ) val owner: DbUser,
) {
    fun toModel() = Device(
        id = device.id,
        manufacturer = device.manufacturer,
        model = device.model,
        friendlyName = device.friendlyName,
        displayName = device.displayName,
        owner = owner.toModel(),
        batteryState = Device.BatteryState.NotShared,
    )
}
