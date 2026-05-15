package es.jvbabi.trails.domain.repository

interface LocationRepository {
    suspend fun storeLocation(latitude: Double, longitude: Double)
}