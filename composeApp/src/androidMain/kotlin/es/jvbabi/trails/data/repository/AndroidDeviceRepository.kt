package es.jvbabi.trails.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidDeviceRepository : DeviceRepository, KoinComponent {

    private val context by inject<Context>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun getDeviceModel(): String {
        val model = android.os.Build.MODEL
        if (model == "sdk_gphone64_arm64") return "tokay" // TODO: remove for prod, just a test to make it behave like a real device
        return model
    }

    override fun getManufacturer(): String {
        return android.os.Build.MANUFACTURER
    }

    override fun getBatteryState(): SharedFlow<BatteryState> {
        return callbackFlow {
            val batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    intent?.let {
                        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1

                        val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL

                        trySend(BatteryState(percentage, isCharging))
                    }
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val initialIntent = context.registerReceiver(batteryReceiver, filter)

            initialIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                trySend(BatteryState(percentage, isCharging))
            }

            awaitClose {
                context.unregisterReceiver(batteryReceiver)
            }
        }
            .distinctUntilChanged()
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1,
            )
    }
}
