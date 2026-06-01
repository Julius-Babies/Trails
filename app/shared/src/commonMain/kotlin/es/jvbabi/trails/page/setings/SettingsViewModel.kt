package es.jvbabi.trails.page.setings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.permissions.*
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import dev.icerock.moko.permissions.notifications.REMOTE_NOTIFICATION
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.domain.usecase.SetupNotificationsUseCase
import es.jvbabi.trails.openUrl
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SettingsViewModel(
    private val deviceRepository: DeviceRepository,
    private val keyValueRepository: KeyValueRepository,
    private val devicesRepository: DevicesRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository,
    private val setupNotificationsUseCase: SetupNotificationsUseCase,
    private val permissionsController: PermissionsController,
) : ViewModel() {

    val state: StateFlow<SettingsState>
        field = MutableStateFlow(SettingsState())

    init {
        viewModelScope.launch {
            while (isActive) {
                state.update {
                    it.copy(
                        hasLocationPermissions = permissionsController.getPermissionState(Permission.LOCATION) == PermissionState.Granted &&
                                permissionsController.getPermissionState(Permission.BACKGROUND_LOCATION) == PermissionState.Granted,
                        hasNotificationPermissions = permissionsController.getPermissionState(Permission.REMOTE_NOTIFICATION) == PermissionState.Granted,
                    )
                }
                delay(1.seconds)
            }
        }

        viewModelScope.launch {
            deviceRepository.hasFullScreenIntentPermissions().collect { hasPermissions ->
                state.update { it.copy(hasFullscreenIntentPermissions = hasPermissions) }
            }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.UserId).collect { userId ->
                state.update { it.copy(userId = userId) }
            }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.Host).collect { homeserver ->
                state.update { it.copy(currentHomeserverUrl = homeserver) }
            }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.ThisDeviceId)
                .collectLatest { deviceId ->
                    state.update { it.copy(thisDeviceId = deviceId) }
                    if (deviceId != null) devicesRepository.getDeviceById(deviceId).collectLatest { device ->
                        state.update { it.copy(thisDevice = device) }
                    } else {
                        state.update { it.copy(thisDevice = null) }
                    }
                }
        }

        viewModelScope.launch {
            backgroundServiceRepository.isRunning().collect { isRunning ->
                state.update { it.copy(isBackgroundTrackingServiceRunning = isRunning) }
            }
        }

        viewModelScope.launch {
            keyValueRepository.get(Key.Theme)
                .map { it ?: Theme.System }
                .collect { theme ->
                    state.update { it.copy(appTheme = theme) }
                }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OpenLoginDialog -> state.update { it.copy(showLoginDialog = true) }
            is SettingsEvent.CloseLoginDialog -> state.update { it.copy(showLoginDialog = false) }
            is SettingsEvent.UpdateHomeServerUrl -> state.update { it.copy(homeServerUrl = event.url) }
            is SettingsEvent.Login -> {
                state.update { it.copy(showLoginDialog = false) }
                val url = URLBuilder(state.value.homeServerUrl).apply {
                    if (!state.value.homeServerUrl.startsWith("http://") && !state.value.homeServerUrl.startsWith("https://")) protocol =
                        URLProtocol.HTTPS
                    appendPathSegments("api", "v1", "auth", "app-authorization")
                    parameters.append("device_manufacturer", deviceRepository.getManufacturer())
                    parameters.append("device_model", deviceRepository.getDeviceModel())
                }.buildString()

                openUrl(url)
            }

            is SettingsEvent.RequestLocationPermissions -> {
                viewModelScope.launch {
                    try {
                        permissionsController.providePermission(Permission.LOCATION)
                    } catch (_: DeniedException) {
                        permissionsController.openAppSettings()
                        return@launch
                    }
                    try {
                        permissionsController.providePermission(Permission.BACKGROUND_LOCATION)
                    } catch (_: DeniedAlwaysException) {
                        permissionsController.openAppSettings()
                    } catch (_: DeniedException) {
                        permissionsController.openAppSettings()
                    }
                }
            }

            is SettingsEvent.RequestNotificationPermissions -> {
                viewModelScope.launch {
                    try {
                        permissionsController.providePermission(Permission.REMOTE_NOTIFICATION)
                        setupNotificationsUseCase()
                    } catch (_: DeniedException) {
                        permissionsController.openAppSettings()
                        return@launch
                    }
                }
            }

            is SettingsEvent.RequestFullscreenIntentPermissions -> deviceRepository.requestFullScreenIntentPermissions()
            is SettingsEvent.StartTracking -> viewModelScope.launch { backgroundServiceRepository.startService() }
            is SettingsEvent.StopTracking -> backgroundServiceRepository.stopService()
            is SettingsEvent.RingDevice -> deviceRepository.startRinging("Settings") {}
            is SettingsEvent.SetAppTheme -> viewModelScope.launch {
                keyValueRepository.set(Key.Theme, event.theme)
            }
        }
    }
}

data class SettingsState(
    val homeServerUrl: String = "https://trailsdevelopment.jvbabi.es", // TODO remove default value for prod, just for testing
    val showLoginDialog: Boolean = false,
    val hasLocationPermissions: Boolean? = null,
    val hasNotificationPermissions: Boolean? = null,
    val hasFullscreenIntentPermissions: Boolean? = null,
    val isBackgroundTrackingServiceRunning: Boolean = false,
    val currentHomeserverUrl: String? = null,
    val userId: Uuid? = null,
    val thisDeviceId: Uuid? = null,
    val thisDevice: Device? = null,

    val appTheme: Theme? = null,
)

sealed class SettingsEvent {
    data object OpenLoginDialog : SettingsEvent()
    data object CloseLoginDialog : SettingsEvent()
    data object Login : SettingsEvent()
    data class UpdateHomeServerUrl(val url: String) : SettingsEvent()
    data object RequestLocationPermissions : SettingsEvent()
    data object RequestNotificationPermissions : SettingsEvent()
    data object RequestFullscreenIntentPermissions : SettingsEvent()
    data object StartTracking : SettingsEvent()
    data object StopTracking : SettingsEvent()
    data object RingDevice : SettingsEvent()
    data class SetAppTheme(val theme: Theme) : SettingsEvent()
}