@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbDataSnapshot
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.utils.Location
import es.jvbabi.trails.utils.distanceTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(FlowPreview::class)
class SnapshotRepositoryImpl(
    private val locationRepository: LocationRepository,
    private val deviceRepository: DeviceRepository,
    private val devicesRepository: DevicesRepository,
    private val keyValueRepository: KeyValueRepository,
    private val database: TrailsDatabase,
): SnapshotRepository {

    private var job: Job? = null
    private val snapshotsByDevice = mutableMapOf<Uuid, Snapshot>()

    override fun startSnapshotCollection(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val device = keyValueRepository
            .get(Key.ThisDeviceId)
            .filterNotNull()
            .flatMapLatest { id -> devicesRepository.getDeviceById(id) }
            .filterNotNull()

        val locationFlow = keyValueRepository
            .get(Key.MinimumMovementDistanceToNextSnapshot)
            .map { it!! } // Should never be null
            .flatMapLatest { minimumDistanceInMeters ->
                locationRepository
                    .getCurrentLocation()
                    .filterNotNull()
                    .distinctUntilChanged { old, new ->
                        Location(latitude = old.latitude, longitude = old.longitude) distanceTo Location(latitude = new.latitude, longitude = new.longitude) < minimumDistanceInMeters
                    }
            }

        job = combine(
            locationFlow,
            deviceRepository.getBatteryState(),
            device,
        ) { location, batteryState, device ->
            Snapshot(
                id = Uuid.random(),
                device = device,
                time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                location = location,
                batteryState = batteryState,
                isSynced = false,
            )
        }
            .onEach {
                storeSnapshot(it)
            }
            .launchIn(scope)
    }

    override fun getSnapshotById(id: Uuid): Flow<Snapshot?> {
        return database.dataSnapshotDao.getSnapshotById(id).map { it?.toModel() }
    }

    override suspend fun storeSnapshot(snapshot: Snapshot) {
        val deviceId = snapshot.device.id

        snapshotsByDevice[deviceId] = snapshot
        val timestamp = snapshot.time.toInstant(TimeZone.currentSystemDefault()).epochSeconds

        database.dataSnapshotDao.upsert(
            DbDataSnapshot(
                id = snapshot.id,
                timestamp = timestamp,
                deviceId = snapshot.device.id,
                latitude = snapshot.location.latitude,
                longitude = snapshot.location.longitude,
                bearing = snapshot.location.bearing,
                bearingAccuracy = snapshot.location.bearingAccuracy,
                locationAccuracy = snapshot.location.locationAccuracy,
                batteryLevel = snapshot.batteryState?.percentage?.div(100f),
                batteryCharging = snapshot.batteryState?.isCharging,
                isSynced = snapshot.isSynced,
            )
        )
    }

    override suspend fun getUnsyncedSnapshots(
        deviceId: Uuid,
        olderThan: Instant,
        excludedIds: Collection<Uuid>,
        limit: Int,
    ): List<Snapshot> {
        return database.dataSnapshotDao.getUnsyncedSnapshots(
            deviceId = deviceId,
            maxTimestamp = olderThan.epochSeconds,
            excludedIds = excludedIds,
            limit = limit,
        ).map { it.toModel() }
    }

    override fun getUnsyncedSnapshotCount(deviceId: Uuid): Flow<Int> {
        return database.dataSnapshotDao.getUnsyncedSnapshotCount(deviceId)
    }

    override fun getCurrentSnapshotForDevice(device: Device): Flow<Snapshot?> {
        return database.dataSnapshotDao.getLastSnapshot(device.id)
            .map { snapshot ->
                val model = snapshot?.toModel()
                if (model != null) {
                    snapshotsByDevice[device.id] = model
                }
                snapshotsByDevice[device.id]
            }
    }
}

