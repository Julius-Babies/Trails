package es.jvbabi.trails.data

import database.DataSnapshot
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class DeviceSubscriptionRepository: KoinComponent {
    private val db by inject<DatabaseManager>()

    private val deviceSubscriptions = mutableMapOf<Uuid, MutableSharedFlow<DeviceSubscriptionMessage>>()
    private val deviceSubscriptionsMutex = Mutex()

    suspend fun getFlowForDeviceSubscription(deviceId: Uuid): MutableSharedFlow<DeviceSubscriptionMessage> {
        return deviceSubscriptionsMutex.withLock {
            deviceSubscriptions.getOrPut(deviceId) {
                val device = db.transaction { Device.findById(deviceId) }
                if (device == null) {
                    return@getOrPut MutableStateFlow(DeviceSubscriptionMessage.Deleted)
                }

                return@getOrPut MutableSharedFlow()
            }
        }
    }
}

sealed class DeviceSubscriptionMessage {
    data object Deleted : DeviceSubscriptionMessage()
    data class Snapshot(val snapshot: DataSnapshot): DeviceSubscriptionMessage()
}
