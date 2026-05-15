package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface KeyValueRepository {
    suspend fun setValue(key: String, value: String)
    fun get(key: String): Flow<String?>
}