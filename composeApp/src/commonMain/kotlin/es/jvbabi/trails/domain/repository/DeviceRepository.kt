package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getDeviceModel(): String
    fun getManufacturer(): String
    fun getBatteryState(): Flow<BatteryState>
}

data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
)