package es.jvbabi.trails.ui.overlay.update_available

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import es.jvbabi.trails.domain.usecase.app.AppVersionState
import es.jvbabi.trails.domain.usecase.app.CheckAppIsLatestVersionUseCase
import es.jvbabi.trails.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateAvailableViewModel(
    private val trailsAppRepository: TrailsAppRepository,
    private val checkAppIsLatestVersionUseCase: CheckAppIsLatestVersionUseCase,
) : ViewModel() {
    val state: StateFlow<UpdateAvailableState?>
        field = MutableStateFlow(null)

    init {
        viewModelScope.launch {
            // Anything but a confirmed newer release (including a failed check) keeps the state
            // null, which hides the overlay.
            val updateAvailable = checkAppIsLatestVersionUseCase() as? AppVersionState.UpdateAvailable
                ?: return@launch
            state.update {
                UpdateAvailableState(
                    currentVersion = trailsAppRepository.getCurrentVersion(),
                    latestVersion = updateAvailable.version,
                    downloadLink = updateAvailable.downloadLink,
                )
            }
        }
    }

    fun onEvent(event: UpdateAvailableEvent) {
        when (event) {
            is UpdateAvailableEvent.RequestDismiss -> state.update { it!!.copy(isDismissed = true) }
            is UpdateAvailableEvent.Install -> {
                val downloadLink = state.value?.downloadLink ?: return
                openUrl(downloadLink)
                state.update { it!!.copy(isDismissed = true) }
            }
        }
    }
}

data class UpdateAvailableState(
    val currentVersion: String,
    val latestVersion: String? = null,
    val downloadLink: String? = null,
    val isDismissed: Boolean = false,
)

sealed class UpdateAvailableEvent {
    data object RequestDismiss: UpdateAvailableEvent()
    data object Install: UpdateAvailableEvent()
}