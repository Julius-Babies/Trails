package es.jvbabi.trails.data

import es.jvbabi.trails.api.v1.entity.Share as ShareEntity
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.DbShare
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface ShareRepository {
    fun getShare(id: String, context: RequestContext = RequestContext.LOCAL): Flow<Share?>
}

class ShareRepositoryProxy: ShareRepository, KoinComponent {
    private val localShareRepository by inject<LocalShareRepository>()
    private val remoteRepositoryStore by inject<RemoteRepositoryStore>()
    private val applicationConfig by inject<ApplicationConfig>()

    override fun getShare(id: String, context: RequestContext): Flow<Share?> {
        val shareId = ShareId(id)
        if (shareId.host == applicationConfig.url.host) {
            // Local lookups own the data and ignore the request context.
            return localShareRepository.getShare(shareId.id)
        }

        return RemoteShareRepository(remoteRepositoryStore.get(shareId.host)).getShare(shareId.id, context)
    }
}

class LocalShareRepository: KoinComponent {
    private val database by inject<DatabaseManager>()

    fun getShare(id: Uuid): Flow<LocalShare?> {
        return flow {
            database.transaction {
                DbShare
                    .findById(id)
                    ?.let(::LocalShare)
            }.let { emit(it) }
        }
    }
}

class RemoteShareRepository(
    private val remoteRepository: RemoteRepository,
) {
    fun getShare(id: Uuid, context: RequestContext): Flow<RemoteShare?> {
        return flow {
            val response = remoteRepository.httpClient.get("/api/v1/share/$id") {
                context.applyTo(this)
            }
            if (response.status == HttpStatusCode.NotFound) {
                emit(null)
                return@flow
            }
            val body = response.body<ShareEntity>()
            emit(object : RemoteShare(
                homeserver = remoteRepository.baseUrl.host,
                shareId = body.id.toString(),
                deviceId = body.deviceId.toString(),
            ) {
                override val shareName: String = body.shareName
                override val locationHistorySeconds: Int = body.locationHistorySeconds
                override val shareBatteryState: Boolean = body.shareBatteryState
                override val allowMultiuse: Boolean = body.allowMultiuse
                override val isLocked: Boolean = body.isLocked
            })
        }
    }
}
