package es.jvbabi.trails.data.repository

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import es.jvbabi.trails.android.RingService
import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

class AndroidDeviceRepository : DeviceRepository, KoinComponent {

    private val context by inject<Context>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var onStopCallback: (() -> Unit)? = null
    private val _ringStopReceived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val ringStopReceived: SharedFlow<Unit> = _ringStopReceived.asSharedFlow()

    override fun getDeviceModel(): String {
        val model = Build.MODEL
        if (model == "sdk_gphone64_arm64") return "tokay" // TODO: remove for prod, just a test to make it behave like a real device
        return model
    }

    override fun getManufacturer(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) {
            Build.MANUFACTURER
        } else {
            "Unknown Manufacturer"
        }
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

    override fun hasFullScreenIntentPermissions(): Flow<Boolean> {
        return flow {
            while (true) {
                val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(NotificationManager::class.java)
                } else {
                    emit(true)
                    return@flow
                }

                val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    service.canUseFullScreenIntent()
                } else {
                    emit(true)
                    return@flow
                }

                emit(hasPermissions)
                delay(1.seconds)
            }
        }.distinctUntilChanged()
    }

    override fun requestFullScreenIntentPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK  // ← add this
                    }
                )
            }
        }
    }

    override fun startRinging(causedByDeviceName: String, onStop: () -> Unit) {
        onStopCallback = onStop
        val intent = Intent(context, RingService::class.java).apply {
            action = RingService.ACTION_START
            putExtra(RingService.EXTRA_DEVICE_NAME, causedByDeviceName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopRinging() {
        onStopCallback?.invoke()
        onStopCallback = null
        _ringStopReceived.tryEmit(Unit)
        val intent = Intent(context, RingService::class.java).apply {
            action = RingService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun hasDisabledBackgroundBatteryOptimization(): Flow<Boolean> {
        return flow {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            while (currentCoroutineContext().isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    emit(powerManager.isIgnoringBatteryOptimizations(context.packageName))
                } else {
                    emit(true)
                    return@flow
                }
                delay(1.seconds)
            }
        }.distinctUntilChanged()
    }

    override fun requestDisableBackgroundBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
