package es.jvbabi.trails.domain.model

import kotlin.uuid.Uuid

data class Device(
    val id: Uuid,
    val manufacturer: String,
    val model: String,
    val friendlyName: String,
    val displayName: String,
    val owner: User,
    val batteryState: BatteryState
) {
    sealed class BatteryState {
        data object NotShared: BatteryState()
        data class Shared(val percentage: Int, val isCharging: Boolean): BatteryState()
    }
}