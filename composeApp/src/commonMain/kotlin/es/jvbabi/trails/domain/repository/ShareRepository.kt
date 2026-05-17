package es.jvbabi.trails.domain.repository

import kotlin.time.Duration
import kotlin.uuid.Uuid

interface ShareRepository {
    suspend fun createShare(
        locationHistory: Duration,
        withBatteryState: Boolean,
        shareName: String,
        allowMultiuse: Boolean
    ): ShareCreationResult
}

sealed class ShareCreationResult {
    data class Success(val shareId: Uuid, val homeServer: String): ShareCreationResult()
    data class Error(val errorMessage: String): ShareCreationResult()
}