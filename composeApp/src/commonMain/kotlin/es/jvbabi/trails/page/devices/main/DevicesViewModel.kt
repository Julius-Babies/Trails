package es.jvbabi.trails.page.devices.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class DevicesViewModel(
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
    private val keyValueRepository: KeyValueRepository,
) : ViewModel() {
    val state: StateFlow<DevicesState>
        field = MutableStateFlow(DevicesState())

    init {
        viewModelScope.launch {
            keyValueRepository.get("trails.userId")
                .map { it?.let { Uuid.parse(it) } }
                .collectLatest { userId ->
                    getHomeDeviceLocationsUseCase().collectLatest { devices ->
                        state.update { it.copy(
                            myDevices = devices.filter { device -> device.device.owner.id == userId },
                            foreignDevices = devices.filter { device -> device.device.owner.id != userId },
                        ) }
                    }
                }
        }
    }
}

data class DevicesState(
    val myDevices: List<HomeState.HomeDevice> = emptyList(),
    val foreignDevices: List<HomeState.HomeDevice> = emptyList(),
)