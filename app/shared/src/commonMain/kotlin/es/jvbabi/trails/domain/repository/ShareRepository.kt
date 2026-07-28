package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.ActiveShare
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.uuid.Uuid

interface ShareRepository {
    suspend fun createShare(
        locationHistory: Duration,
        withBatteryState: Boolean,
        shareName: String,
        allowMultiuse: Boolean
    ): ShareCreationResult

    fun getShares(): Flow<List<ActiveShare>>
    fun getShareById(id: Uuid): Flow<ActiveShare?>

    /**
     * The shares that grant access to [deviceId]. Empty for an own device; more than
     * one entry when several links for the same device were redeemed.
     */
    fun getSharesForDevice(deviceId: Uuid): Flow<List<ActiveShare>>
}

sealed class ShareCreationResult {
    data class Success(val shareId: Uuid, val homeServer: String): ShareCreationResult()
    data class Error(val errorMessage: String): ShareCreationResult()
}