package es.jvbabi.trails.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidLocationService: Service(), LocationListener, KoinComponent {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val locationRepository by inject<LocationRepository>()
    private val permissionsController by inject<PermissionsController>()
    private val trailsServerRepository by inject<TrailsServerRepository>()
    private val keyValueRepository by inject<KeyValueRepository>()

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            if (permissionsController.getPermissionState(Permission.BACKGROUND_LOCATION) != PermissionState.Granted) {
                Log.w("LocationService", "Background location permission not granted, cannot start tracking")
                return@launch
            }
            if (permissionsController.getPermissionState(Permission.LOCATION) != PermissionState.Granted) {
                Log.w("LocationService", "Location permission not granted, cannot start tracking")
                return@launch
            }

            _isRunning.value = true

            if (keyValueRepository.get("trails.token").first() != null) {
                trailsServerRepository.connect()
            } else {
                Log.w("LocationService", "No token found, cannot connect to server")
            }

            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }

            withContext(Dispatchers.Main) {
                startTracking()
            }

        }
        return START_STICKY
    }

    private fun startTracking() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10000L,
                0f,
                this
            )

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L,
                    0f,
                    this
                )
            }

        } catch (unlikely: SecurityException) {
            Log.e("LocationService", "Keine Berechtigung: $unlikely")
        }
    }

    override fun onLocationChanged(location: Location) {
        serviceScope.launch {
            locationRepository.storeLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                bearing = location.bearing,
                bearingAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) location.bearingAccuracyDegrees else null,
                locationAccuracy = location.accuracy,
                batteryLevel = getBatteryPercentage()?.div(100f)
            )
        }
        Log.d("LocationService", "Native Location: ${location.latitude}, ${location.longitude} (Provider: ${location.provider})")
    }

    override fun onProviderEnabled(provider: String) { Log.d("LocationService", "$provider aktiviert") }
    override fun onProviderDisabled(provider: String) { Log.d("LocationService", "$provider deaktiviert") }

    private fun createNotification(): Notification {
        val channelId = "pure_location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Google-freies Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS-Tracking läuft")
            .setContentText("Standort wird alle 10 Sekunden nativ erfasst.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        locationManager.removeUpdates(this)
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun getBatteryPercentage(): Int? {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = applicationContext.registerReceiver(null, intentFilter)

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            (level * 100 / scale)
        } else {
            null
        }
    }
}
