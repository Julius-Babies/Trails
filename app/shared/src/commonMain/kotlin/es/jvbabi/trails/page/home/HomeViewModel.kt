@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class HomeViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val locationRepository: LocationRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository,
    private val trailsServerRepository: TrailsServerRepository,
    private val devicesRepository: DevicesRepository,
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
): ViewModel() {

    val state: StateFlow<HomeState>
        field = MutableStateFlow(HomeState())

    init {
        viewModelScope.launch {
            locationRepository.getCurrentLocation().collect { location ->
                state.value = state.value.copy(ownLocation = location)
            }
        }

        viewModelScope.launch {
            keyValueRepository.get("trails.thisDeviceId")
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { devicesRepository.getDeviceById(Uuid.parse(it)) }
                .collectLatest {
                    state.update { it.copy(currentDevice = it.currentDevice) }
                }
        }

        viewModelScope.launch {
            keyValueRepository.get("trails.userId")
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    backgroundServiceRepository.startService()
                    trailsServerRepository.updateUserDevices()
                    trailsServerRepository.getMeData()
                }
        }

        viewModelScope.launch {
            getHomeDeviceLocationsUseCase().collect { devices ->
                state.update { it.copy(devices = devices) }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectTab -> {
                state.update { it.copy(selectedTab = event.tab) }
            }
        }
    }
}

data class HomeState(
    val ownLocation: Location? = null,
    val selectedTab: Tab = Tab.MyDevices,
    val currentDevice: Device? = null,
    val devices: List<HomeDevice> = emptyList(),
) {
    enum class Tab {
        MyDevices, Things, Shares
    }

    data class HomeDevice(
        val device: Device,
        val image: ByteArray?,
        val snapshot: Snapshot?,
    )
}

sealed class HomeEvent {
    data class SelectTab(val tab: HomeState.Tab): HomeEvent()
}