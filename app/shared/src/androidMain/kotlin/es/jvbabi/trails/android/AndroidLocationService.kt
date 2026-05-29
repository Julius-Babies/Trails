@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.SnapshotRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class AndroidLocationService: Service(), LocationListener, KoinComponent {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val locationRepository by inject<LocationRepository>()
    private val snapshotRepository by inject<SnapshotRepository>()
    private val devicesRepository by inject<DevicesRepository>()
    private val keyValueRepository by inject<KeyValueRepository>()
    private val trailsServerRepository by inject<TrailsServerRepository>()

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trails:LocationWakeLock")
        wakeLock?.acquire(1.hours.inWholeMilliseconds)

        serviceScope.launch {
            keyValueRepository
                .get("trails.thisDeviceId")
                .filterNotNull()
                .flatMapLatest { id ->
                    try {
                        devicesRepository.getDeviceById(Uuid.parse(id))
                    } catch (e: IllegalArgumentException) {
                        Logger.e(e) { "Invalid device ID in store: $id" }
                        kotlinx.coroutines.flow.flowOf(null)
                    }
                }
                .filterNotNull()
                .distinctUntilChangedBy {}
                .collect {
                    trailsServerRepository.connectWithHomeserver().await()
                }
        }
        serviceScope.launch {
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
        _isRunning.value = true
        snapshotRepository.startSnapshotCollection(serviceScope)
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
            )
        }
        Logger.d { "Native Location: ${location.latitude}, ${location.longitude} (Provider: ${location.provider})" }
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
        wakeLock?.release()
        wakeLock = null
        serviceScope.cancel()
        locationManager.removeUpdates(this)
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
