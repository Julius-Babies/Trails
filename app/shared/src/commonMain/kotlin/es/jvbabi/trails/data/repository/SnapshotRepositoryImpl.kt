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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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
    private val snapshotsByDevice = mutableMapOf<Uuid, Snapshot>()
    private val lastDbTimestamps = mutableMapOf<Uuid, Long>()

    override fun startSnapshotCollection(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val device = keyValueRepository
            .get("trails.thisDeviceId")
            .filterNotNull()
            .flatMapLatest { id ->
                try {
                    devicesRepository.getDeviceById(Uuid.parse(id))
                } catch (_: IllegalArgumentException) {
                    flowOf(null)
                }
            }
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
                storeSnapshot(it)
            }
            .launchIn(scope)
    }

    override suspend fun storeSnapshot(snapshot: Snapshot) {
        val deviceId = snapshot.device.id
        val previousSnapshot = snapshotsByDevice[deviceId]
        val previousDbTimestamp = lastDbTimestamps[deviceId]
        val batteryChanged = previousSnapshot?.batteryState != snapshot.batteryState
        val movedEnough = previousSnapshot?.let { previous ->
            distanceMeters(
                previous.location.latitude,
                previous.location.longitude,
                snapshot.location.latitude,
                snapshot.location.longitude,
            ) > MIN_DISTANCE_METERS
        } ?: true

        snapshotsByDevice[deviceId] = snapshot
        val timestamp = snapshot.time.toInstant(TimeZone.currentSystemDefault()).epochSeconds

        if (batteryChanged || movedEnough || previousDbTimestamp == null) {
            database.dataSnapshotDao.upsert(
                DbDataSnapshot(
                    timestamp = timestamp,
                    deviceId = snapshot.device.id,
                    latitude = snapshot.location.latitude,
                    longitude = snapshot.location.longitude,
                    bearing = snapshot.location.bearing,
                    bearingAccuracy = snapshot.location.bearingAccuracy,
                    locationAccuracy = snapshot.location.locationAccuracy,
                    batteryLevel = snapshot.batteryState?.percentage?.div(100f),
                    batteryCharging = snapshot.batteryState?.isCharging,
                )
            )
            lastDbTimestamps[deviceId] = timestamp
        } else {
            database.dataSnapshotDao.updateTimestamp(deviceId, previousDbTimestamp, timestamp)
            lastDbTimestamps[deviceId] = timestamp
        }
    }

    override fun getCurrentSnapshotForDevice(device: Device): Flow<Snapshot?> {
        return database.dataSnapshotDao.getLastSnapshot(device.id)
            .map { snapshot ->
                val model = snapshot?.toModel()
                if (model != null) {
                    snapshotsByDevice[device.id] = model
                    lastDbTimestamps[device.id] = snapshot.dataSnapshot.timestamp
                }
                snapshotsByDevice[device.id]
            }
    }
}

private const val MIN_DISTANCE_METERS = 10.0
private const val EARTH_RADIUS_METERS = 6371000.0

private fun distanceMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val lat1 = toRadians(latitude1)
    val lat2 = toRadians(latitude2)
    val deltaLat = toRadians(latitude2 - latitude1)
    val deltaLon = toRadians(longitude2 - longitude1)

    val a = sin(deltaLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

private fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)
