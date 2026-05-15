package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface BackgroundServiceRepository {
    fun startService()
    fun stopService()
    fun isRunning(): Flow<Boolean>
}