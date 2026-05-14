package es.jvbabi.trails.page.setings

import androidx.lifecycle.ViewModel
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.openUrl
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    private val deviceRepository: DeviceRepository,
): ViewModel() {

    val state: StateFlow<SettingsState>
        field = MutableStateFlow(SettingsState())

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
                    parameters.append("device_name", deviceRepository.getDeviceName())
                }.buildString()

                openUrl(url)
            }
        }
    }
}

data class SettingsState(
    val homeServerUrl: String = "",
    val showLoginDialog: Boolean = false,
)

sealed class SettingsEvent {
    data object OpenLoginDialog: SettingsEvent()
    data object CloseLoginDialog: SettingsEvent()
    data object Login: SettingsEvent()
    data class UpdateHomeServerUrl(val url: String): SettingsEvent()
}