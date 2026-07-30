package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface DeviceRepository {
    fun getDeviceModel(): String
    fun getManufacturer(): String

    /**
     * ABIs this device can run, most preferred first, in Android's naming (`arm64-v8a`,
     * `armeabi-v7a`, `x86`, `x86_64`) — the same names our release APKs are split by.
     *
     * Empty on platforms that don't self-update (iOS ships through the App Store).
     */
    fun getSupportedAbis(): List<String>

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