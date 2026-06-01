package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface DeviceRepository {
    fun getDeviceModel(): String
    fun getManufacturer(): String
    fun getBatteryState(): Flow<BatteryState>

    fun hasFullScreenIntentPermissions(): Flow<Boolean>
    fun requestFullScreenIntentPermissions()

    fun hasDisabledBackgroundBatteryOptimization(): Flow<Boolean>
    fun requestDisableBackgroundBatteryOptimization()

    fun startRinging(
        causedByDeviceName: String,
        onStop: () -> Unit
    )
    fun stopRinging()
    val ringStopReceived: SharedFlow<Unit>
}

data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
)