package es.jvbabi.trails.domain.model

import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.Location
import kotlinx.datetime.LocalDateTime

data class Snapshot(
    val device: Device,
    val time: LocalDateTime,
    val location: Location,
    val batteryState: BatteryState?,
)