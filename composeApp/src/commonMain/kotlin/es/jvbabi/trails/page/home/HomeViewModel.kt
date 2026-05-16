package es.jvbabi.trails.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val locationRepository: LocationRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository,
    private val trailsServerRepository: TrailsServerRepository,
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
            keyValueRepository.get("trails.token")
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    backgroundServiceRepository.startService()

                    trailsServerRepository.isConnected.collectLatest { isConnected ->
                        state.update { it.copy(isConnectedToServer = isConnected) }
                    }
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
    val isConnectedToServer: Boolean = false,
    val selectedTab: Tab = Tab.MyDevices
) {
    enum class Tab {
        MyDevices, Things, Shares
    }
}

sealed class HomeEvent {
    data class SelectTab(val tab: HomeState.Tab): HomeEvent()
}