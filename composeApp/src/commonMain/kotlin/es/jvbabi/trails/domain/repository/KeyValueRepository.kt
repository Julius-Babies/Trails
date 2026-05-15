package es.jvbabi.trails.domain.repository

interface KeyValueRepository {
    suspend fun setValue(key: String, value: String)
}