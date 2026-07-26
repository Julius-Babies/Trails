package es.jvbabi.trails.data

import es.jvbabi.trails.api.v1.entity.ActiveShare as ActiveShareEntity
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.ActiveShare as DbActiveShare
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface ActiveShareRepository {
    fun getActiveShare(id: String, context: RequestContext = RequestContext.LOCAL): Flow<ActiveShare?>
}

class ActiveShareRepositoryProxy: ActiveShareRepository, KoinComponent {
    private val localActiveShareRepository by inject<LocalActiveShareRepository>()
    private val remoteRepositoryStore by inject<RemoteRepositoryStore>()
    private val applicationConfig by inject<ApplicationConfig>()

    override fun getActiveShare(id: String, context: RequestContext): Flow<ActiveShare?> {
        val activeShareId = ActiveShareId(id)
        if (activeShareId.host == applicationConfig.url.host) {
            // Local lookups own the data and ignore the request context.
            return localActiveShareRepository.getActiveShare(activeShareId.id)
        }

        return RemoteActiveShareRepository(remoteRepositoryStore.get(activeShareId.host))
            .getActiveShare(activeShareId.id, context)
    }
}

class LocalActiveShareRepository: KoinComponent {
    private val database by inject<DatabaseManager>()

    fun getActiveShare(id: Uuid): Flow<LocalActiveShare?> {
        return flow {
            database.transaction {
                DbActiveShare
                    .findById(id)
                    ?.let(::LocalActiveShare)
            }.let { emit(it) }
        }
    }
}

class RemoteActiveShareRepository(
    private val remoteRepository: RemoteRepository,
) {
    fun getActiveShare(id: Uuid, context: RequestContext): Flow<RemoteActiveShare?> {
        return flow {
            val response = remoteRepository.httpClient.get("/api/v1/active-shares/$id") {
                context.applyTo(this)
            }
            if (response.status == HttpStatusCode.NotFound) {
                emit(null)
                return@flow
            }
            val body = response.body<ActiveShareEntity>()
            emit(object : RemoteActiveShare(
                homeserver = remoteRepository.baseUrl.host,
                activeShareId = body.id.toString(),
                shareId = body.shareId.toString(),
            ) {})
        }
    }
}
