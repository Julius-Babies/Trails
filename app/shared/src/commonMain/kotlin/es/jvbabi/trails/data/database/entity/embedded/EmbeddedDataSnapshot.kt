package es.jvbabi.trails.data.database.entity.embedded

import androidx.room.Embedded
import androidx.room.Relation
import es.jvbabi.trails.data.database.entity.DbDataSnapshot
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.Location
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

data class EmbeddedDataSnapshot(
    @Embedded val dataSnapshot: DbDataSnapshot,
    @Relation(
        parentColumn = "device_id",
        entityColumn = "id",
        entity = DbDevice::class
    ) val device: EmbeddedDevice,
) {
    fun toModel(): Snapshot {
        val timestamp = Instant
            .fromEpochSeconds(dataSnapshot.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        return Snapshot(
            device = device.toModel(),
            time = timestamp,
            location = Location(
                latitude = dataSnapshot.latitude,
                longitude = dataSnapshot.longitude,
                bearing = dataSnapshot.bearing,
                bearingAccuracy = dataSnapshot.bearingAccuracy,
                locationAccuracy = dataSnapshot.locationAccuracy,
                time = timestamp
            ),
            batteryState = if (dataSnapshot.batteryLevel != null && dataSnapshot.batteryCharging != null) BatteryState(
                percentage = (dataSnapshot.batteryLevel * 100).roundToInt(),
                isCharging = dataSnapshot.batteryCharging
            ) else null
        )
    }
}
