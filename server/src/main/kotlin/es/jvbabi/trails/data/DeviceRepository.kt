package es.jvbabi.trails.data

import es.jvbabi.trails.api.v1.entity.Device as DeviceEntity
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device as DbDevice
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface DeviceRepository {
    fun getDevice(id: String, context: RequestContext = RequestContext.LOCAL): Flow<Device?>
}

class DeviceRepositoryProxy: DeviceRepository, KoinComponent {
    private val localDeviceRepository by inject<LocalDeviceRepository>()
    private val remoteRepositoryStore by inject<RemoteRepositoryStore>()
    private val applicationConfig by inject<ApplicationConfig>()

    override fun getDevice(id: String, context: RequestContext): Flow<Device?> {
        val deviceId = DeviceId(id)
        if (deviceId.host == applicationConfig.url.host) {
            // Local lookups own the data and ignore the request context.
            return localDeviceRepository.getDevice(deviceId.id)
        }

        return RemoteDeviceRepository(remoteRepositoryStore.get(deviceId.host)).getDevice(deviceId.id, context)
    }
}

class LocalDeviceRepository: KoinComponent {
    private val database by inject<DatabaseManager>()

    fun getDevice(id: Uuid): Flow<LocalDevice?> {
        return flow {
            database.transaction {
                DbDevice
                    .findById(id)
                    ?.let(::LocalDevice)
            }.let { emit(it) }
        }
    }
}

class RemoteDeviceRepository(
    private val remoteRepository: RemoteRepository,
) {
    fun getDevice(id: Uuid, context: RequestContext): Flow<RemoteDevice?> {
        return flow {
            val response = remoteRepository.httpClient.get("/api/v1/devices/$id") {
                context.applyTo(this)
            }
            if (response.status == HttpStatusCode.NotFound) {
                emit(null)
                return@flow
            }
            val body = response.body<DeviceEntity>()
            emit(object : RemoteDevice(
                homeserver = remoteRepository.baseUrl.host,
                deviceId = body.id.toString(),
                ownerId = body.ownerId.toString(),
            ) {
                override val manufacturer: String = body.manufacturer
                override val model: String = body.model
                override val friendlyName: String = body.friendlyName
                override val displayName: String = body.displayName
            })
        }
    }
}
