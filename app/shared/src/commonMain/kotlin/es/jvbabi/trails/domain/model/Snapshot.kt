package es.jvbabi.trails.domain.model

import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.Location
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

data class Snapshot(
    val id: Uuid,
    val device: Device,
    val time: LocalDateTime,
    val location: Location,
    val batteryState: BatteryState?,
    val isSynced: Boolean,
)