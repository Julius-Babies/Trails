package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class LocationRepositoryImpl: LocationRepository {

    private val location = MutableStateFlow<Location?>(null)

    override suspend fun storeLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float,
        bearingAccuracy: Float?,
        locationAccuracy: Float,
    ) {
        this.location.value = Location(
            latitude = latitude,
            longitude = longitude,
            bearing = bearing,
            bearingAccuracy = bearingAccuracy,
            locationAccuracy = locationAccuracy,
            time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }

    override fun getCurrentLocation(): Flow<Location?> {
        return location
    }
}