package es.jvbabi.trails.data.repository

import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.ShareCreationResult
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.utils.NetworkRequestUnsuccessfulException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.Uuid

class ShareRepositoryImpl(
    private val httpClient: HttpClient,
    private val trailsServerRepository: TrailsServerRepository,
): ShareRepository {
    override suspend fun createShare(
        locationHistory: Duration,
        withBatteryState: Boolean,
        shareName: String,
        allowMultiuse: Boolean
    ): ShareCreationResult {
        val token = trailsServerRepository.getToken().first() ?: throw IllegalStateException("No token available")
        val url = (trailsServerRepository.getBaseUrl().first() ?: throw IllegalStateException("No server URL available")).apply {
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
