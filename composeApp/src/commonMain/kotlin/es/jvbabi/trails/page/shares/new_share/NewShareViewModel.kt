@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.shares.new_share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlin.uuid.Uuid

class NewShareViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val devicesRepository: DevicesRepository,
    private val fileRepository: FileRepository,
): ViewModel() {
    val state: StateFlow<NewShareState>
        field = MutableStateFlow(NewShareState())

    init {
        viewModelScope.launch {
            keyValueRepository
                .get("trails.thisDeviceId")
                .filterNotNull()
                .flatMapLatest { devicesRepository.getDeviceById(Uuid.parse(it)) }
                .filterNotNull()
                .collectLatest { device ->
                    state.value = state.value.copy(
                        currentDevice = device,
                    )

                    // Wait for the device image to be available
                    devicesRepository.hasDeviceImage(device).filter { it }.first()
                    state.value = state.value.copy(
                        image = fileRepository.readFile(devicesRepository.getFileNameForDeviceImage(device))
                    )
                }
        }
    }

    fun onEvent(event: NewShareEvent) {
        when (event) {
            is NewShareEvent.LocationShareHistoryStateChanged -> {
                state.value = state.value.copy(
                    selectedLocationShareHistoryState = event.newState,
                )
            }
            NewShareEvent.ShareBatteryLevelChanged -> {
                state.value = state.value.copy(
                    shareBatteryLevel = !state.value.shareBatteryLevel,
                )
            }
            is NewShareEvent.ShareNameChanged -> {
                state.value = state.value.copy(
                    shareName = event.newName,
                )
            }
            NewShareEvent.AllowMultiuseLinkChanged -> {
                state.value = state.value.copy(
                    allowMultiuseLink = !state.value.allowMultiuseLink,
                )
            }
        }
    }
}

data class NewShareState(
    val image: ByteArray? = null,
    val currentDevice: Device? = null,
    val selectedLocationShareHistoryState: LocationShareHistoryState = LocationShareHistoryState.OneHour,
    val shareBatteryLevel: Boolean = true,
    val shareName: String = "",
    val allowMultiuseLink: Boolean = false,
) {
    enum class LocationShareHistoryState(val duration: Duration) {
        NoHistory(0.seconds),
        OneHour(1.hours),
        SixHours(6.hours),
        OneDay(24.hours),
        OneWeek(7 * 24.hours),
        Infinite(Duration.INFINITE),
    }
}

sealed class NewShareEvent {
    data class LocationShareHistoryStateChanged(val newState: NewShareState.LocationShareHistoryState): NewShareEvent()
    data object ShareBatteryLevelChanged: NewShareEvent()
    data class ShareNameChanged(val newName: String): NewShareEvent()
    data object AllowMultiuseLinkChanged: NewShareEvent()
}