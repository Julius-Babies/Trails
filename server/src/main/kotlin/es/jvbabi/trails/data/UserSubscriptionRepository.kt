package es.jvbabi.trails.data

import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.DeviceDeletion
import es.jvbabi.trails.routes.app.AppSocketMessage
import es.jvbabi.trails.shared.dto.DeviceResponse
import es.jvbabi.trails.shared.dto.websocket.TrailsWebSocketServerMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class UserSubscriptionRepository: KoinComponent {
    private val db by inject<DatabaseManager>()

    private val userSubscriptions = mutableMapOf<Uuid, MutableSharedFlow<UserSubscriptionMessage>>()
    private val userSubscriptionsMutex = Mutex()

    suspend fun getFlowForUser(userId: Uuid): MutableSharedFlow<UserSubscriptionMessage> {
        return userSubscriptionsMutex.withLock {
            userSubscriptions.getOrPut(userId) {
                MutableSharedFlow()
            }
        }
    }
}

sealed class UserSubscriptionMessage: KoinComponent {
    data class DeviceUpdated(val device: Device): UserSubscriptionMessage()
    data class DeviceDeleted(val deletion: DeviceDeletion): UserSubscriptionMessage()

    private val db by inject<DatabaseManager>()
    suspend fun toAppSocketMessage(
        principal: TrailsAppUserPrincipal,
    ): AppSocketMessage? {
        when (this) {
            is DeviceUpdated -> {
                if (db.transaction { principal.user.id.value != device.owner.id.value }) return null
                return AppSocketMessage(TrailsWebSocketServerMessage.DeviceUpdated(
                    data = DeviceResponse(
                        id = device.id.value.toString(),
                        manufacturer = device.manufacturer,
                        model = device.model,
                        friendlyName = device.friendlyName,
                        displayName = device.displayName,
                        ownerId = db.transaction { device.owner.id.value.toString() },
                    )
                ))
            }
            is DeviceDeleted -> {
                if (db.transaction { principal.user.id.value != deletion.device.owner.id.value }) return null
                return AppSocketMessage(TrailsWebSocketServerMessage.DeviceDeleted(
                    deletedByDeviceName = db.transaction { deletion.deletedBy.device.displayName },
                    deviceId = db.transaction { deletion.device.id.value.toString() },
                ))
            }
        }
    }
}