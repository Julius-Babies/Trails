@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.devices.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class DeviceViewModel(
    private val devicesRepository: DevicesRepository,
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
    private val keyValueRepository: KeyValueRepository,
    private val userRepository: UserRepository,
    private val trailsServerRepository: TrailsServerRepository,
): ViewModel() {

    val state: StateFlow<DeviceState>
        field = MutableStateFlow(DeviceState())

    private val deviceId = MutableStateFlow<Uuid?>(null)

    fun init(deviceId: Uuid) {
        this.deviceId.value = deviceId
    }

    init {
        viewModelScope.launch {
            deviceId
                .filterNotNull()
                .flatMapLatest { deviceId -> devicesRepository.getDeviceById(deviceId) }
                .filterNotNull()
                .flatMapLatest { device -> getHomeDeviceLocationsUseCase.getHomeDevice(device) }
                .collectLatest { device ->
                    state.update { it.copy(device = device, deletionState = null) }
                }
        }

        viewModelScope.launch {
            keyValueRepository.get("trails.userId")
                .filterNotNull()
                .map { Uuid.parse(it) }
                .flatMapLatest { userRepository.getUser(it) }
                .collectLatest { user ->
                    state.update { it.copy(currentUser = user) }
                }
        }
    }

    fun onEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.Delete -> viewModelScope.launch {
                if (state.value.device == null) return@launch
                if (state.value.deletionState is DeviceState.DeletionState.Loading) return@launch
                state.update { it.copy(deletionState = DeviceState.DeletionState.Loading) }
                try {
                    val result = trailsServerRepository.deleteDevice(state.value.device!!.device)
                    if (result.isSuccess) state.update { it.copy(deletionState = DeviceState.DeletionState.Success) }
                    else if (result.isFailure) state.update { it.copy(deletionState = DeviceState.DeletionState.Error(result.exceptionOrNull()?.message ?: "Unknown error")) }
                } catch (e: Exception) {
                    state.update { it.copy(deletionState = DeviceState.DeletionState.Error(e.message ?: "Unknown error")) }
                }
            }
        }
    }
}

data class DeviceState(
    val device: HomeState.HomeDevice? = null,
    val currentUser: User? = null,

    val deletionState: DeletionState? = null,
) {
    sealed class DeletionState {
        data object Loading: DeletionState()
        data object Success: DeletionState()
        data class Error(val message: String): DeletionState()
    }
}

sealed class DeviceEvent {
    data object Delete: DeviceEvent()
}