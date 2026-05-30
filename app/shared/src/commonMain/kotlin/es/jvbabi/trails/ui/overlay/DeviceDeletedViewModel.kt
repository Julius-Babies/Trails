package es.jvbabi.trails.ui.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.IsDeviceDeletedState
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceDeletedViewModel(
    private val trailsServerRepository: TrailsServerRepository,
    private val fileRepository: FileRepository,
    private val devicesRepository: DevicesRepository,
): ViewModel() {
    val state: StateFlow<DeviceDeletedState?>
        field = MutableStateFlow(null)

    init {
        viewModelScope.launch {
            trailsServerRepository.isDeviceDeletedState.collectLatest { deletedState ->
                when (deletedState) {
                    is IsDeviceDeletedState.Unset -> state.value = null
                    is IsDeviceDeletedState.Deleted -> {
                        state.update {
                            DeviceDeletedState(
                                deletedByDevice = deletedState.deletedByDeviceName,
                                device = deletedState.thisDevice,
                                image = fileRepository.readFile(devicesRepository.getFileNameForDeviceImage(deletedState.thisDevice))
                            )
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: DeviceDeletedEvent) {
        when (event) {
            is DeviceDeletedEvent.RequestDismiss -> state.update { it?.copy(isDismissed = true) }
            is DeviceDeletedEvent.Dismissed -> viewModelScope.launch { trailsServerRepository.resetDeviceDeletedState() }
        }
    }
}

data class DeviceDeletedState(
    val deletedByDevice: String,
    val device: Device,
    val image: ByteArray? = null,
    val isDismissed: Boolean = false,
)

sealed class DeviceDeletedEvent {
    data object Dismissed: DeviceDeletedEvent()
    data object RequestDismiss: DeviceDeletedEvent()
}