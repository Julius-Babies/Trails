@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class DevicesRepositoryImpl(
    private val database: TrailsDatabase,
    private val fileRepository: FileRepository,
    private val keyValueRepository: KeyValueRepository,
    private val deviceRepository: DeviceRepository,
) : DevicesRepository {

    private fun deviceProxy(device: Device): Flow<Device> {
        return keyValueRepository.get("trails.thisDeviceId")
            .map { it?.let { Uuid.parse(it) } }
            .flatMapLatest { thisDeviceId ->
                if (thisDeviceId != device.id) flowOf(device)
                else deviceRepository.getBatteryState().map { batteryState ->
                    device.copy(
                        batteryState = Device.BatteryState.Shared(
                            percentage = batteryState.percentage,
                            isCharging = batteryState.isCharging,
                        )
                    )
                }
            }
    }

    private fun devicesProxy(devices: List<Device>): Flow<List<Device>> {
        if (devices.isEmpty()) return flowOf(emptyList())
        return combine(
            devices.map { deviceProxy(it) }
        ) { it.toList() }
    }

    override fun getDevices(user: User): Flow<List<Device>> {
        return database.deviceDao.getDevicesByOwner(user.id)
            .map { items -> items.map { it.toModel() } }
            .flatMapLatest { devices -> devicesProxy(devices) }
    }

    override fun getDevices(): Flow<List<Device>> {
        return database.deviceDao.getDevices()
            .map { items -> items.map { it.toModel() } }
            .flatMapLatest { devices -> devicesProxy(devices) }
    }

    override fun getDeviceById(id: Uuid): Flow<Device?> {
        return database.deviceDao.getDeviceById(id)
            .map { embeddedDevice -> embeddedDevice?.toModel() }
            .flatMapLatest { device -> device?.let { deviceProxy(it) } ?: flowOf(null) }
    }

    override fun hasDeviceImage(device: Device): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            val fileName = getFileNameForDeviceImage(device)
            emit(fileRepository.hasFile(fileName))
            delay(2.seconds)
        }
    }.distinctUntilChanged()
}