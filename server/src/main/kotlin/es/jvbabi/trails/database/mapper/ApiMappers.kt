package es.jvbabi.trails.database.mapper

import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.Share
import es.jvbabi.trails.database.User
import es.jvbabi.trails.api.v1.entity.ActiveShare as ActiveShareEntity
import es.jvbabi.trails.api.v1.entity.Device as DeviceEntity
import es.jvbabi.trails.api.v1.entity.Share as ShareEntity
import es.jvbabi.trails.api.v1.entity.User as UserEntity

/**
 * Wire-Entity-Mapper. Alle greifen auf Relationen zu (owner/device/share) und
 * müssen daher innerhalb einer [es.jvbabi.trails.database.DatabaseManager.transaction] aufgerufen werden.
 */

fun Device.toApi() = DeviceEntity(
    id = id.value,
    manufacturer = manufacturer,
    model = model,
    friendlyName = friendlyName,
    displayName = displayName,
    ownerId = owner.id.value,
)

fun User.toApi() = UserEntity(
    id = id.value,
    username = username,
)

fun Share.toApi() = ShareEntity(
    id = id.value,
    deviceId = device.id.value,
    shareName = shareName,
    locationHistorySeconds = locationHistorySeconds,
    shareBatteryState = shareBatteryState,
    allowMultiuse = allowMultiuse,
    isLocked = isLocked,
)

fun ActiveShare.toApi() = ActiveShareEntity(
    id = id.value,
    shareId = share.id.value,
)
