package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbLocation
import es.jvbabi.trails.domain.repository.LocationRepository
import kotlin.time.Clock

class LocationRepositoryImpl(
    private val database: TrailsDatabase,
): LocationRepository {
    override suspend fun storeLocation(latitude: Double, longitude: Double) {
        database.locationDao.upsert(
            DbLocation(
                latitude = latitude,
                longitude = longitude,
                timestamp = Clock.System.now().epochSeconds
            )
        )
    }
}