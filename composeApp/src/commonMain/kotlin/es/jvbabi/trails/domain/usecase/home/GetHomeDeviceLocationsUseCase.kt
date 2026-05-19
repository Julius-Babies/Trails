@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.domain.usecase.home

import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.domain.repository.SnapshotRepository
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class GetHomeDeviceLocationsUseCase(
    private val devicesRepository: DevicesRepository,
    private val shareRepository: ShareRepository,
    private val snapshotRepository: SnapshotRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    private val keyValueRepository: KeyValueRepository,
) {
    operator fun invoke(): Flow<List<HomeState.HomeDevice>> {
        return keyValueRepository.get("trails.userId")
            .filterNotNull()
            .flatMapLatest { userRepository.getUser(Uuid.parse(it)) }
            .filterNotNull()
            .distinctUntilChangedBy { it.id }
            .flatMapLatest { user ->
                val ownedDevices = devicesRepository.getDevices(user)
                val sharedDevices = shareRepository.getShares()
                    .map { shares -> shares.map { it.device } }

                combine(ownedDevices, sharedDevices) { owned, shared ->
                    (owned + shared).distinctBy { it.id }
                }.flatMapLatest { devices ->
                    if (devices.isEmpty()) return@flatMapLatest flowOf(emptyList())

                    combine(devices.map { device ->
                        getHomeDevice(device)
                    }) { homeDevices ->
                        homeDevices.toList()
                    }
                }
            }
    }

    private fun getHomeDevice(device: Device): Flow<HomeState.HomeDevice> {
        val snapshotFlow = snapshotRepository.getCurrentSnapshotForDevice(device)

        val imageFlow = devicesRepository.hasDeviceImage(device)
            .map { hasImage ->
                if (hasImage) {
                    fileRepository.readFile(devicesRepository.getFileNameForDeviceImage(device))
                } else {
                    null
                }
            }
            .distinctUntilChanged()

        return combine(snapshotFlow, imageFlow) { snapshot, image ->
            HomeState.HomeDevice(
                device = device,
                image = image,
                snapshot = snapshot,
            )
        }
    }
}
