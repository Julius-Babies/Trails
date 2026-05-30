@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.home

import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.utils.IntPaddingValues
import es.jvbabi.trails.utils.toMapCamera
import kotlin.math.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class HomeViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val locationRepository: LocationRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository,
    private val trailsServerRepository: TrailsServerRepository,
    private val devicesRepository: DevicesRepository,
    private val getHomeDeviceLocationsUseCase: GetHomeDeviceLocationsUseCase,
) : ViewModel() {

    val state: StateFlow<HomeState>
        field = MutableStateFlow(HomeState())

    private var viewportDimensions = MutableStateFlow<IntSize?>(null)
    var mapContentPadding = MutableStateFlow<IntPaddingValues?>(null)
    private val localDensity = MutableStateFlow<Float?>(null)
    private val followDevice = MutableStateFlow<Device?>(null)

    init {
        viewModelScope.launch(CoroutineName("Start service if user exists + update user data")) {
            val doesUserExist = keyValueRepository.get("trails.userId").first() != null
            if (!doesUserExist) return@launch

            val sessionHealth = trailsServerRepository.checkSessionHealth()
            if (sessionHealth is SessionHealthState.InvalidOrExpired || sessionHealth is SessionHealthState.NoSessionExpected) return@launch
            if (sessionHealth is SessionHealthState.Error) {
                Logger.e { "Failed to get session health: ${sessionHealth.errorMessage}" }
                return@launch
            }

            trailsServerRepository.getMeData()
            trailsServerRepository.updateUserDevices()
            backgroundServiceRepository.startService()
        }

        viewModelScope.launch(CoroutineName("OwnLocation")) {
            locationRepository.getCurrentLocation().collect { location ->
                state.update { it.copy(ownLocation = location) }
            }
        }

        viewModelScope.launch(CoroutineName("This device")) {
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

        viewModelScope.launch(CoroutineName("All devices")) {
            getHomeDeviceLocationsUseCase()
                .collectLatest { devices ->
                    state.update { it.copy(devices = devices) }
                }
        }

        viewModelScope.launch(CoroutineName("Update camera position")) {
            val stateFlow = state
                .distinctUntilChangedBy { listOf(
                    it.trackingMode,
                    it.ownLocation,
                    it.devices
                ).map { it.hashCode() }.sum() }
                .map {
                    object {
                        val devices = it.devices
                        val trackingMode = it.trackingMode
                        val ownLocation = it.ownLocation
                    }
                }

            val measurementsFlow =
                combine(viewportDimensions.filterNotNull(), mapContentPadding.filterNotNull()) { viewportDimensions, mapContentPadding ->
                    object {
                        val viewport = viewportDimensions;
                        val contentPadding = mapContentPadding
                    }
                }

            combine(
                stateFlow,
                measurementsFlow,
                localDensity.filterNotNull(),
                followDevice,
            ) { emission, measurements, localDensity, followDevice ->

                if (followDevice != null) {
                    val bounds = calculateBounds(null, emission.devices.filter { device -> device.device.id == followDevice.id }) ?: return@combine
                    val cameraState = bounds.toMapCamera(
                        viewportWidthPx = measurements.viewport.width,
                        viewportHeightPx = measurements.viewport.height,
                        density = localDensity,
                        padding = measurements.contentPadding,
                        defaultZoom = 18.0,
                        minZoom = 0.0,
                    )
                    state.update { it.copy(targetCameraState = cameraState) }
                } else when (emission.trackingMode) {
                    HomeState.TrackingMode.None -> {
                        state.update { it.copy(targetCameraState = null) }
                    }
                    HomeState.TrackingMode.Overview -> {
                        val bounds = calculateBounds(emission.ownLocation, emission.devices) ?: return@combine
                        val cameraState = bounds.toMapCamera(
                            viewportWidthPx = measurements.viewport.width,
                            viewportHeightPx = measurements.viewport.height,
                            density = localDensity,
                            padding = measurements.contentPadding,
                            defaultZoom = 18.0,
                            minZoom = 0.0,
                        )

                        state.update { it.copy(targetCameraState = cameraState) }
                    }

                    HomeState.TrackingMode.OwnLocation -> {
                        if (emission.ownLocation == null) return@combine
                        state.update {
                            it.copy(targetCameraState = HomeState.MapCamera(
                                centerLatitude = emission.ownLocation.latitude,
                                centerLongitude = emission.ownLocation.longitude,
                                zoom = 20.0,
                                pitch = 70.0,
                                bearing = emission.ownLocation.bearing.toDouble()
                            ))
                        }
                    }
                }
            }.collectLatest {}
        }
    }

    fun setup(localDensity: Float) {
        this.localDensity.update { localDensity }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectTab -> state.update { it.copy(selectedTab = event.tab) }
            is HomeEvent.UserDragged -> {
                state.update { it.copy(trackingMode = HomeState.TrackingMode.None) }
                followDevice.update { null }
            }
            is HomeEvent.ToggleTrackingMode -> state.update {
                val next = when (it.trackingMode) {
                    HomeState.TrackingMode.None -> HomeState.TrackingMode.Overview
                    HomeState.TrackingMode.Overview -> HomeState.TrackingMode.OwnLocation
                    HomeState.TrackingMode.OwnLocation -> HomeState.TrackingMode.Overview
                }
                it.copy(trackingMode = next)
            }

            is HomeEvent.OnViewportResize -> viewportDimensions.update { event.viewportDimensions }
            is HomeEvent.OnMapContentAreaPadding -> mapContentPadding.update { event.mapContentPadding }
            is HomeEvent.FocusDevice -> followDevice.update { state.value.devices.firstOrNull { device -> device.device.id == event.deviceId }?.device }
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
            
            val distinctCoords = mutableListOf<Pair<Double, Double>>()
            for (coord in coords) {
                if (distinctCoords.none { distanceInMeters(it.first, it.second, coord.first, coord.second) < 20.0 }) {
                    distinctCoords.add(coord)
                }
            }

            if (distinctCoords.isEmpty()) return null
            return HomeState.FitBounds(coordinates = distinctCoords)
        }

        private fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadius = 6371000.0
            val dLat = (lat2 - lat1) * (PI / 180.0)
            val dLon = (lon2 - lon1) * (PI / 180.0)
            val a = (sin(dLat / 2) * sin(dLat / 2) +
                    cos(lat1 * (PI / 180.0)) * cos(lat2 * (PI / 180.0)) *
                    sin(dLon / 2) * sin(dLon / 2)).coerceIn(0.0, 1.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadius * c
        }
    }
}

data class HomeState(
    val ownLocation: Location? = null,
    val selectedTab: Tab = Tab.MyDevices,
    val currentDevice: Device? = null,
    val devices: List<HomeDevice> = emptyList(),
    val targetCameraState: MapCamera? = null,
    val fitBounds: FitBounds? = null,
    val trackingMode: TrackingMode = TrackingMode.Overview,
) {
    enum class Tab {
        MyDevices, Things, Shares
    }

    enum class TrackingMode {
        Overview, OwnLocation, None
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
    data class SelectTab(val tab: HomeState.Tab) : HomeEvent()
    data object UserDragged : HomeEvent()
    data object ToggleTrackingMode : HomeEvent()

    data class OnViewportResize(val viewportDimensions: IntSize) : HomeEvent()
    data class OnMapContentAreaPadding(val mapContentPadding: IntPaddingValues) : HomeEvent()
    data class FocusDevice(val deviceId: Uuid?): HomeEvent()
}