package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface LocationRepository {
    suspend fun storeLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float,
    )
    fun getCurrentLocation(): Flow<Location?>
}

data class Location(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val time: LocalDateTime,
)