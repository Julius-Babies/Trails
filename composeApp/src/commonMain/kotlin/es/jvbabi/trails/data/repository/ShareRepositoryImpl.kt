package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.domain.model.ActiveShare
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.ShareCreationResult
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.Uuid

class ShareRepositoryImpl(
    private val httpClient: HttpClient,
    private val database: TrailsDatabase,
    private val keyValueRepository: KeyValueRepository,
): ShareRepository {
    override suspend fun createShare(
        locationHistory: Duration,
        withBatteryState: Boolean,
        shareName: String,
        allowMultiuse: Boolean
    ): ShareCreationResult {
        val token = keyValueRepository.get("trails.token").first() ?: return ShareCreationResult.Error("No token available")
        val url = (keyValueRepository.get("trails.host").first()?.let { URLBuilder("https://$it") } ?: throw IllegalStateException("No server URL available")).apply {
            appendPathSegments("api", "v1", "app", "share")
        }.build()

        val response = httpClient.post(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(NewShareRequest(
                historyDurationSeconds = locationHistory.inWholeSeconds.toInt(),
                batteryState = withBatteryState,
                shareName = shareName,
                allowMultiuse = allowMultiuse,
            ))
        }

        if (!response.status.isSuccess()) {
            Logger.e(NetworkRequestUnsuccessfulException(response)) { "Failed to create share" }
            return ShareCreationResult.Error("Failed to create share: ${response.status}")
        }

        val shareResponse = response.body<ShareResponse>()
        return ShareCreationResult.Success(Uuid.parse(shareResponse.shareId), homeServer = url.host)
    }

    override fun getShares(): Flow<List<ActiveShare>> {
        return database.activeShareDao.getActiveShares()
            .map { shares -> shares.map { it.toModel() } }
    }
}

@Serializable
data class NewShareRequest(
    @SerialName("history_duration_seconds") val historyDurationSeconds: Int,
    @SerialName("battery_state") val batteryState: Boolean,
    @SerialName("share_name") val shareName: String,
    @SerialName("allow_multiuse") val allowMultiuse: Boolean,
)

@Serializable
data class ShareResponse(
    @SerialName("share_id") val shareId: String,
)
