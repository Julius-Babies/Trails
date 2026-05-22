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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.darwin.NSObject
import platform.posix.uname
import platform.posix.utsname

class IosDeviceRepository : DeviceRepository {

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
}
