@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
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
                state.update { it.copy(ownLocation = location) }
            }
        }

        viewModelScope.launch {
            keyValueRepository.get("trails.thisDeviceId")
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    try {
                        devicesRepository.getDeviceById(Uuid.parse(id))
                    } catch (e: IllegalArgumentException) {
                        Logger.e(e) { "Invalid device ID: $id" }
                        flowOf(null)
                    }
                }
                .collectLatest { device ->
                    state.update { it.copy(currentDevice = device) }
                }
        }

        viewModelScope.launch {
            keyValueRepository.get("trails.userId")
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest {
                    flow {
                        backgroundServiceRepository.startService()
                        trailsServerRepository.updateUserDevices()
                        trailsServerRepository.getMeData()
                        emitAll(
                            combine(
                                getHomeDeviceLocationsUseCase(),
                                locationRepository.getCurrentLocation().onStart { emit(null) },
                            ) { devices, location -> devices to location }
                        )
                    }
                }
                .collect { (devices, location) ->
                    state.update {
                        it.copy(
                            ownLocation = location ?: it.ownLocation,
                            devices = devices,
                        )
                    }
                    if (!state.value.initialFitDone) {
                        val bounds = calculateBounds(location ?: state.value.ownLocation, devices)
                        if (bounds != null) {
                            state.update { it.copy(fitBounds = bounds, initialFitDone = true) }
                        }
                    }
                }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectTab -> state.update { it.copy(selectedTab = event.tab) }
            is HomeEvent.FlyTo -> state.update {
                it.copy(
                    mapCamera = event.camera,
                    flyToSignal = it.flyToSignal + 1,
                    flyToAnimated = event.animated,
                    initialFitDone = true,
                )
            }
            is HomeEvent.OnCameraChanged -> state.update { it.copy(mapCamera = event.camera) }
        }
    }

    companion object {
        fun calculateBounds(location: Location?, devices: List<HomeState.HomeDevice>): HomeState.FitBounds? {
            val coords = mutableListOf<Pair<Double, Double>>()
            location?.let { coords.add(it.latitude to it.longitude) }
            devices.forEach { device ->
                val snapshot = device.snapshot ?: return@forEach
                coords.add(snapshot.location.latitude to snapshot.location.longitude)
            }
            if (coords.isEmpty()) return null
            return HomeState.FitBounds(coordinates = coords)
        }
    }
}

data class HomeState(
    val ownLocation: Location? = null,
    val selectedTab: Tab = Tab.MyDevices,
    val currentDevice: Device? = null,
    val devices: List<HomeDevice> = emptyList(),
    val mapCamera: MapCamera? = null,
    val fitBounds: FitBounds? = null,
    val flyToSignal: Int = 0,
    val flyToAnimated: Boolean = false,
    val initialFitDone: Boolean = false,
) {
    enum class Tab {
        MyDevices, Things, Shares
    }

    data class MapCamera(
        val centerLatitude: Double,
        val centerLongitude: Double,
        val zoom: Double,
        val pitch: Double,
        val bearing: Double,
    )

    data class FitBounds(
        val coordinates: List<Pair<Double, Double>>,
    )

    data class HomeDevice(
        val device: Device,
        val image: ByteArray?,
        val snapshot: Snapshot?,
    )
}

sealed class HomeEvent {
    data class SelectTab(val tab: HomeState.Tab): HomeEvent()
    data class FlyTo(val camera: HomeState.MapCamera, val animated: Boolean = true): HomeEvent()
    data class OnCameraChanged(val camera: HomeState.MapCamera): HomeEvent()
}