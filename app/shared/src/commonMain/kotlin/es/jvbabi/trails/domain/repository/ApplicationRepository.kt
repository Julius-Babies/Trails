package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow

interface ApplicationRepository {
    fun getApplicationForegroundState(): Flow<Boolean>

    /** Whether this build is a debug build rather than a shipped release. */
    val isDebugBuild: Boolean
}