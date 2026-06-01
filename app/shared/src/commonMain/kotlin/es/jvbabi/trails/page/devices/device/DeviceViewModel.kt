@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.devices.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.PingResult
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UiRepository
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class DeviceViewModel(
    private val deviceRepository: DeviceRepository,
    private val devicesRepository: DevicesRepository,
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
    private val keyValueRepository: KeyValueRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    private val uiRepository: UiRepository,
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
                    state.update { it.copy(
                        device = device,
                        deletionState = null,
                        image = if (it.device?.device?.id != device.device.id) null else it.image,
                    ) }

                    devicesRepository.hasDeviceImage(device.device).first { it }
                    state.value = state.value.copy(
                        image = fileRepository.readFile(devicesRepository.getFileNameForDeviceImage(device.device))
                    )
                }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.UserId)
                .filterNotNull()
                .flatMapLatest { userRepository.getUser(it) }
                .collectLatest { user ->
                    state.update { it.copy(currentUser = user) }
                }
        }

        viewModelScope.launch {
            state
                .filter { it.device != null && it.currentUser != null }
                .distinctUntilChangedBy { listOf(it.device!!.device.id, it.currentUser!!.id).sumOf { it.hashCode() } }
                .collectLatest { snapshot ->
                    val isOwnDevice = snapshot.device!!.device.owner.id == snapshot.currentUser!!.id

                    state.update { it.copy(
                        pingState = when {
                            it.pingState == null && isOwnDevice -> DeviceState.PingState.Ready
                            !isOwnDevice -> DeviceState.PingState.Disabled
                            else -> it.pingState
                        },
                        ringState = when {
                            it.ringState == null && isOwnDevice -> DeviceState.RingState.Ready
                            !isOwnDevice -> DeviceState.RingState.Disabled
                            else -> it.ringState
                        }
                    ) }
                }
        }

        viewModelScope.launch {
            trailsServerRepository.ringStates
                .combine(deviceId.filterNotNull()) { states, id ->
                    states[id]
                }
                .collectLatest { deviceRingState ->
                    state.update { it.copy(
                        ringState = when {
                            deviceRingState?.isRinging == true -> DeviceState.RingState.Ringing
                            it.ringState == DeviceState.RingState.Ringing -> DeviceState.RingState.Ready
                            else -> it.ringState
                        }
                    ) }
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

            is DeviceEvent.Ping -> {
                viewModelScope.launch {
                    state.update { it.copy(pingState = DeviceState.PingState.Loading) }
                    try {
                        when (val result = trailsServerRepository.requestPing(state.value.device!!.device)) {
                            is PingResult.Pinged -> {
                                uiRepository.sendSnackbar(when (result.hasDeliveredNotification) {
                                    true -> "Das Gerät wurde gefunden."
                                    false -> "Das Gerät hat geantwortet, konnte die Benachrichtigung jedoch nicht senden."
                                }, autoDismiss = 5.seconds)
                                state.update { it.copy(pingState = DeviceState.PingState.Ready) }
                            }
                            PingResult.NotAllowed -> {
                                uiRepository.sendSnackbar("Du darfst dieses Gerät nicht pingen.", autoDismiss = 5.seconds)
                                state.update { it.copy(pingState = DeviceState.PingState.Disabled) }
                            }
                            PingResult.Timeout -> {
                                uiRepository.sendSnackbar("Das Gerät antwortet nicht.", autoDismiss = 5.seconds)
                                state.update { it.copy(pingState = DeviceState.PingState.Ready) }
                            }
                            is PingResult.Error -> {
                                uiRepository.sendSnackbar("Ein Fehler ist aufgetreten: ${result.errorMessage}", autoDismiss = 5.seconds)
                                state.update { it.copy(pingState = DeviceState.PingState.Ready) }
                            }
                        }
                    } catch (e: Exception) {
                        uiRepository.sendSnackbar("Ein Fehler ist aufgetreten: ${e.message}", autoDismiss = 5.seconds)
                        state.update { it.copy(pingState = DeviceState.PingState.Ready) }
                    }
                }
            }

            is DeviceEvent.Ring -> {
                trailsServerRepository.requestRing(state.value.device!!.device)
            }

            is DeviceEvent.StopRing -> {
                trailsServerRepository.requestStopRing(state.value.device!!.device)
            }
        }
    }
}

data class DeviceState(
    val device: HomeState.HomeDevice? = null,
    val currentUser: User? = null,
    val pingState: PingState? = null,
    val ringState: RingState? = null,

    val deletionState: DeletionState? = null,
    val image: ByteArray? = null,
) {
    sealed class DeletionState {
        data object Loading: DeletionState()
        data object Success: DeletionState()
        data class Error(val message: String): DeletionState()
    }

    sealed class PingState {
        data object Disabled: PingState()
        data object Loading: PingState()
        data object Ready: PingState()
    }

    sealed class RingState {
        data object Disabled: RingState()
        data object Loading: RingState()
        data object Ready: RingState()
        data object Ringing: RingState()
    }
}

sealed class DeviceEvent {
    data object Delete: DeviceEvent()
    data object Ping: DeviceEvent()
    data object Ring: DeviceEvent()
    data object StopRing: DeviceEvent()
}
