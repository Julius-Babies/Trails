package es.jvbabi.trails.page.setings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.openUrl
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val deviceRepository: DeviceRepository,
    private val permissionsController: PermissionsController,
    private val backgroundServiceRepository: BackgroundServiceRepository,
): ViewModel() {

    val state: StateFlow<SettingsState>
        field = MutableStateFlow(SettingsState())

    init {
        viewModelScope.launch {
            state.update {
                it.copy(
                    hasLocationPermissions = permissionsController.getPermissionState(Permission.LOCATION) == PermissionState.Granted &&
                            permissionsController.getPermissionState(Permission.BACKGROUND_LOCATION) == PermissionState.Granted
                )
            }
        }

        viewModelScope.launch {
            backgroundServiceRepository.isRunning().collect { isRunning ->
                state.update { it.copy(isBackgroundTrackingServiceRunning = isRunning) }
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
                    if (!state.value.homeServerUrl.startsWith("http://") && !state.value.homeServerUrl.startsWith("https://")) protocol = URLProtocol.HTTPS
                    appendPathSegments("api", "v1", "auth", "app-authorization")
                    parameters.append("device_manufacturer", deviceRepository.getManufacturer())
                    parameters.append("device_model", deviceRepository.getDeviceModel())
                }.buildString()

                openUrl(url)
            }
            is SettingsEvent.RequestLocationPermissions -> {
                viewModelScope.launch {
                    permissionsController.providePermission(Permission.LOCATION)
                    try {
                        permissionsController.providePermission(Permission.BACKGROUND_LOCATION)
                    } catch (_: DeniedAlwaysException) {
                        permissionsController.openAppSettings()
                    }
                }
            }
            is SettingsEvent.StartTracking -> viewModelScope.launch { backgroundServiceRepository.startService() }
            is SettingsEvent.StopTracking -> backgroundServiceRepository.stopService()
        }
    }
}

data class SettingsState(
    val homeServerUrl: String = "https://trailsdevelopment.jvbabi.es", // TODO remove default value for prod, just for testing
    val showLoginDialog: Boolean = false,
    val hasLocationPermissions: Boolean? = null,
    val isBackgroundTrackingServiceRunning: Boolean = false,
)

sealed class SettingsEvent {
    data object OpenLoginDialog: SettingsEvent()
    data object CloseLoginDialog: SettingsEvent()
    data object Login: SettingsEvent()
    data class UpdateHomeServerUrl(val url: String): SettingsEvent()
    data object RequestLocationPermissions: SettingsEvent()
    data object StartTracking: SettingsEvent()
    data object StopTracking: SettingsEvent()
}