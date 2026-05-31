package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface ApplicationRepository {
    fun getApplicationForegroundState(): Flow<Boolean>
}