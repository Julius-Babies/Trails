package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface ApplicationRepository {
    fun getApplicationForegroundState(): Flow<Boolean>
}