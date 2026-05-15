package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbLocation
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class LocationRepositoryImpl(
    private val database: TrailsDatabase,
): LocationRepository {
    override suspend fun storeLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float,
        bearingAccuracy: Float?,
        locationAccuracy: Float,
        batteryLevel: Float?
    ) {
        database.locationDao.upsert(
            DbLocation(
                latitude = latitude,
                longitude = longitude,
                bearing = bearing,
                bearingAccuracy = bearingAccuracy,
                locationAccuracy = locationAccuracy,
                batteryLevel = batteryLevel,
                timestamp = Clock.System.now().epochSeconds
            )
        )
    }

    override fun getCurrentLocation(): Flow<Location?> {
        return database.locationDao.getLastLocation().map { dbLocation ->
            dbLocation?.let {
                Location(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    bearing = it.bearing,
                    bearingAccuracy = it.bearingAccuracy,
                    locationAccuracy = it.locationAccuracy,
                    batteryLevel = it.batteryLevel,
                    time = Instant.fromEpochSeconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }
        }
    }
}