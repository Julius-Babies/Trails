package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocationManager

class IosBackgroundServiceRepository : BackgroundServiceRepository {

    private val locationManager = CLLocationManager()

    private val _isRunning = MutableStateFlow(false)

    init {
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.distanceFilter = 50.0
    }

    override suspend fun startService() {
        if (_isRunning.value) return
        locationManager.startUpdatingLocation()
        _isRunning.value = true
    }

    override fun stopService() {
        if (!_isRunning.value) return
        locationManager.stopUpdatingLocation()
        _isRunning.value = false
    }

    override fun isRunning(): Flow<Boolean> = _isRunning.asStateFlow()
}
