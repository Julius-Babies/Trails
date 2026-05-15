package es.jvbabi.trails.page.setings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.COARSE_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
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
                    permissionsController.providePermission(Permission.BACKGROUND_LOCATION)
                }
            }
        }
    }
}

data class SettingsState(
    val homeServerUrl: String = "https://trails.werkbank.space", // TODO remove default value for prod, just for testing
    val showLoginDialog: Boolean = false,
    val hasLocationPermissions: Boolean? = null,
)

sealed class SettingsEvent {
    data object OpenLoginDialog: SettingsEvent()
    data object CloseLoginDialog: SettingsEvent()
    data object Login: SettingsEvent()
    data class UpdateHomeServerUrl(val url: String): SettingsEvent()
    data object RequestLocationPermissions: SettingsEvent()
}