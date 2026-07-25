@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.DeviceRepository
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlayAlertSound
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.darwin.NSObject
import platform.posix.uname
import platform.posix.utsname
import kotlin.time.Duration.Companion.seconds

class IosDeviceRepository : DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ringJob: Job? = null
    private var onStopCallback: (() -> Unit)? = null

    private val _ringStopReceived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val ringStopReceived: SharedFlow<Unit> = _ringStopReceived.asSharedFlow()

    override fun getDeviceModel(): String = memScoped {
        val systemInfo = alloc<utsname>()
        uname(systemInfo.ptr)
        systemInfo.machine.toKString()
    }

    override fun getManufacturer(): String = "Apple"

    override fun getBatteryState(): Flow<BatteryState> = callbackFlow {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true

        fun emitCurrent() {
            val level = device.batteryLevel
            val percentage = if (level >= 0f) (level * 100).toInt() else 0
            val isCharging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging || device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
            trySend(BatteryState(percentage, isCharging))
        }

        emitCurrent()

        val observer = object : NSObject() {
            @ObjCAction
            fun batteryStateChanged() = emitCurrent()
        }

        NSNotificationCenter.defaultCenter.addObserver(
            observer = observer,
            selector = NSSelectorFromString("batteryStateChanged"),
            name = UIDeviceBatteryStateDidChangeNotification,
            `object` = null,
        )
        NSNotificationCenter.defaultCenter.addObserver(
            observer = observer,
            selector = NSSelectorFromString("batteryStateChanged"),
            name = UIDeviceBatteryLevelDidChangeNotification,
            `object` = null,
        )

        awaitClose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            device.batteryMonitoringEnabled = false
        }
    }.distinctUntilChanged()

    // iOS has no equivalent of Android's full-screen-intent permission.
    override fun hasFullScreenIntentPermissions(): Flow<Boolean> = flowOf(true)

    override fun requestFullScreenIntentPermissions() = Unit

    // iOS manages background execution itself; there is nothing for the user to disable.
    override fun hasDisabledBackgroundBatteryOptimization(): Flow<Boolean> = flowOf(true)

    override fun requestDisableBackgroundBatteryOptimization() = Unit

    override fun startRinging(causedByDeviceName: String, onStop: () -> Unit) {
        onStopCallback = onStop
        if (ringJob?.isActive == true) return

        val session = AVAudioSession.sharedInstance()
        try {
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        } catch (_: Throwable) {
            // Ignore audio session setup failures; ringing is best effort.
        }

        ringJob = scope.launch {
            while (isActive) {
                // System alert sound plays and vibrates even while the app is in the foreground.
                AudioServicesPlayAlertSound(ALERT_SOUND_ID)
                delay(2.seconds)
            }
        }
    }

    override fun stopRinging() {
        ringJob?.cancel()
        ringJob = null

        try {
            AVAudioSession.sharedInstance().setActive(false, null)
        } catch (_: Throwable) {
            // Ignore audio session teardown failures.
        }

        onStopCallback?.invoke()
        onStopCallback = null
        _ringStopReceived.tryEmit(Unit)
    }

    private companion object {
        // 1005 = a distinctive built-in alert tone.
        const val ALERT_SOUND_ID: UInt = 1005u
    }
}
