@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbDataSnapshot
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.SnapshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SnapshotRepositoryImpl(
    private val locationRepository: LocationRepository,
    private val deviceRepository: DeviceRepository,
    private val devicesRepository: DevicesRepository,
    private val keyValueRepository: KeyValueRepository,
    private val database: TrailsDatabase,
): SnapshotRepository {

    private var job: Job? = null

    override fun startSnapshotCollection(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val device = keyValueRepository
            .get("trails.thisDeviceId")
            .filterNotNull()
            .flatMapLatest { devicesRepository.getDeviceById(Uuid.parse(it)) }
            .filterNotNull()

        job = combine(
            locationRepository.getCurrentLocation().filterNotNull(),
            deviceRepository.getBatteryState(),
            device,
        ) { location, batteryState, device ->
            Snapshot(
                device = device,
                time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                location = location,
                batteryState = batteryState,
            )
        }
            .onEach {
                database.dataSnapshotDao.upsert(DbDataSnapshot(
                    timestamp = it.time.toInstant(TimeZone.currentSystemDefault()).epochSeconds,
                    deviceId = it.device.id,
                    latitude = it.location.latitude,
                    longitude = it.location.longitude,
                    bearing = it.location.bearing,
                    bearingAccuracy = it.location.bearingAccuracy,
                    locationAccuracy = it.location.locationAccuracy,
                    batteryLevel = it.batteryState?.percentage?.div(100f),
                    batteryCharging = it.batteryState?.isCharging,
                ))
            }
            .launchIn(scope)
    }

    override fun getCurrentSnapshotForDevice(device: Device): Flow<Snapshot?> {
        return database.dataSnapshotDao.getLastSnapshot(device.id)
            .map { it?.toModel() }
    }
}