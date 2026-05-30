package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface KeyValueRepository {
    suspend fun setValue(key: String, value: String)
    suspend fun delete(key: String)
    fun get(key: String): Flow<String?>
}