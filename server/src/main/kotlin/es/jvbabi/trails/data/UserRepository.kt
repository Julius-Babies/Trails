package es.jvbabi.trails.data

import es.jvbabi.trails.api.v1.entity.User as UserEntity
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.DbUser
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface UserRepository {
    fun getUser(id: String, context: RequestContext = RequestContext.LOCAL): Flow<User?>
}

class UserRepositoryProxy: UserRepository, KoinComponent {
    private val localUserRepository by inject<LocalUserRepository>()
    private val remoteRepositoryStore by inject<RemoteRepositoryStore>()
    private val applicationConfig by inject<ApplicationConfig>()

    override fun getUser(id: String, context: RequestContext): Flow<User?> {
        val id = UserId(id)
        if (id.host == applicationConfig.url.host) {
            // Local lookups own the data and ignore the request context.
            return localUserRepository.getUser(id.id)
        }

        return RemoteUserRepository(remoteRepositoryStore.get(id.host)).getUser(id.id, context)
    }
}

class LocalUserRepository: KoinComponent {
    private val database by inject<DatabaseManager>()

    fun getUser(id: Uuid): Flow<LocalUser?> {
        return flow {
            database.transaction {
                DbUser
                    .findById(id)
                    ?.let(::LocalUser)
            }.let { emit(it) }
        }
    }
}

class RemoteUserRepository(
    private val remoteRepository: RemoteRepository,
) {
    fun getUser(id: Uuid, context: RequestContext): Flow<RemoteUser?> {
        return flow {
            val response = remoteRepository.httpClient.get("/api/v1/users/$id") {
                context.applyTo(this)
            }
            if (response.status == HttpStatusCode.NotFound) {
                emit(null)
                return@flow
            }
            val body = response.body<UserEntity>()
            emit(object : RemoteUser(
                homeserver = remoteRepository.baseUrl.host,
                userId = body.id.toString(),
            ) {
                override val username: String = body.username
            })
        }
    }
}
