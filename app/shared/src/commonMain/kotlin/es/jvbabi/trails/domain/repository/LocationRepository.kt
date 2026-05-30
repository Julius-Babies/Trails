package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface LocationRepository {
    suspend fun storeLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float,
        bearingAccuracy: Float?,
        locationAccuracy: Float,
    )
    fun getCurrentLocation(): Flow<Location?>
}

/**
 * @param bearing The bearing in degrees (0..360)
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val bearingAccuracy: Float?,
    val locationAccuracy: Float,
    val time: LocalDateTime,
)