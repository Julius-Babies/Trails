package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface DevicesRepository {
    fun getDevices(user: User): Flow<List<Device>>
    fun getDeviceById(id: Uuid): Flow<Device?>

    fun hasDeviceImage(device: Device): Flow<Boolean>

    fun getFileNameForDeviceImage(device: Device): String {
        return "device_images/${device.manufacturer}-${device.model}.jpg"
    }
}