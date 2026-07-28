package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.api.v1.share.CreateShareRequest
import es.jvbabi.trails.api.v1.share.CreateShareResponse
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.domain.extension.Settings
import es.jvbabi.trails.domain.model.ActiveShare
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.ShareCreationResult
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.uuid.Uuid

class ShareRepositoryImpl(
    private val httpClient: HttpClient,
    private val database: TrailsDatabase,
    private val keyValueRepository: KeyValueRepository,
    private val settings: Settings,
): ShareRepository {
    override suspend fun createShare(
        locationHistory: Duration,
        withBatteryState: Boolean,
        shareName: String,
        allowMultiuse: Boolean
    ): ShareCreationResult {
        val token = keyValueRepository.get(Key.Token).first() ?: return ShareCreationResult.Error.OtherError("No token available")
        val url = settings.getHomeserver().apply {
            appendPathSegments("api", "v1", "share")
        }.build()

        val response = httpClient.post(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateShareRequest(
                deviceId = keyValueRepository.get(Key.ThisDeviceId).first()!!,
                locationHistorySeconds = locationHistory.inWholeSeconds.toInt(),
                shareBattery = withBatteryState,
                shareName = shareName,
                allowMultiuse = allowMultiuse,
            ))
        }

        // A duplicate share name is answered with 409 plus a regular CreateShareResponse
        // body, so that status must not short-circuit before the body is parsed.
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Conflict) {
            Logger.e(NetworkRequestUnsuccessfulException(response)) { "Failed to create share" }
            return ShareCreationResult.Error.OtherError("Failed to create share: ${response.status}")
        }

        return when(val shareResponse = response.body<CreateShareResponse>()) {
            is CreateShareResponse.ShareNameAlreadyExists -> ShareCreationResult.Error.ShareNameAlreadyExists
            is CreateShareResponse.ShareCreated -> ShareCreationResult.Success(shareResponse.shareId, homeServer = url.host)
        }
    }

    override fun getShares(): Flow<List<ActiveShare>> {
        return database.activeShareDao.getActiveShares()
            .map { shares -> shares.map { it.toModel() } }
    }

    override fun getShareById(id: Uuid): Flow<ActiveShare?> {
        return database.activeShareDao.getActiveShareById(id).map { it?.toModel() }
    }

    override fun getSharesForDevice(deviceId: Uuid): Flow<List<ActiveShare>> {
        return database.activeShareDao.getActiveSharesForDevice(deviceId)
            .map { shares -> shares.map { it.toModel() } }
    }
}
