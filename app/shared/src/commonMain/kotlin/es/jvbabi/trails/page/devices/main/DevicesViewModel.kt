@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.devices.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
    private val keyValueRepository: KeyValueRepository,
    private val trailsServerRepository: TrailsServerRepository,
) : ViewModel() {
    val state: StateFlow<DevicesState>
        field = MutableStateFlow(DevicesState())

    init {
        viewModelScope.launch {
            keyValueRepository.get(Key.UserId)
                .collectLatest { userId ->
                    getHomeDeviceLocationsUseCase().collectLatest { devices ->
                        state.update {
                            it.copy(
                                myDevices = devices.filter { device -> device.device.owner.id == userId },
                                foreignDevices = devices.filter { device -> device.device.owner.id != userId },
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.ThisDeviceId)
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { deviceId ->
                    getHomeDeviceLocationsUseCase().collectLatest { devices ->
                        state.update {
                            it.copy(
                                thisDevice = devices.find { device -> device.device.id == deviceId }
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            trailsServerRepository.isConnected.collectLatest { connected ->
                state.update { it.copy(isConnectedToHomeServer = if (connected) DevicesState.HomeServerConnectionState.Connected else DevicesState.HomeServerConnectionState.Disconnected) }
            }
        }
    }
}

data class DevicesState(
    val myDevices: List<HomeState.HomeDevice> = emptyList(),
    val thisDevice: HomeState.HomeDevice? = null,
    val foreignDevices: List<HomeState.HomeDevice> = emptyList(),
    val isConnectedToHomeServer: HomeServerConnectionState? = null
) {
    sealed class HomeServerConnectionState {
        data object Connected : HomeServerConnectionState()
        data object Disconnected : HomeServerConnectionState()
    }
}