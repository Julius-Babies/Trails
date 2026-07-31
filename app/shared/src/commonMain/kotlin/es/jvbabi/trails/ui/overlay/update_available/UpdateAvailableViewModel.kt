package es.jvbabi.trails.ui.overlay.update_available

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import es.jvbabi.trails.domain.usecase.app.AppVersionState
import es.jvbabi.trails.domain.usecase.app.CheckAppIsLatestVersionUseCase
import es.jvbabi.trails.domain.usecase.app.GetReleaseChangelogsUseCase
import es.jvbabi.trails.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateAvailableViewModel(
    private val trailsAppRepository: TrailsAppRepository,
    private val checkAppIsLatestVersionUseCase: CheckAppIsLatestVersionUseCase,
    private val getReleaseChangelogsUseCase: GetReleaseChangelogsUseCase,
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

            // Fetched only after the overlay is already showing: it takes one request per release,
            // and the update prompt must not wait for it.
            val changelog = getReleaseChangelogsUseCase(upToVersion = updateAvailable.version)
            state.update { it?.copy(changelog = changelog, areChangelogsLoading = false) }
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
            // Leaves the overlay open: the user is looking something up, not done updating.
            is UpdateAvailableEvent.OpenIssue -> openUrl(event.url)
        }
    }
}

data class UpdateAvailableState(
    val currentVersion: String,
    val latestVersion: String? = null,
    val downloadLink: String? = null,
    val isDismissed: Boolean = false,

    /**
     * What changed between the running build and [latestVersion], newest version first.
     *
     * `null` when there is nothing to show, which does not distinguish a failed fetch from
     * releases that ship no changelog — neither is worth telling the user about.
     */
    val changelog: Changelog? = null,

    /** Whether the changelog is still on its way, so the UI can show a placeholder. */
    val areChangelogsLoading: Boolean = true,
)

sealed class UpdateAvailableEvent {
    data object RequestDismiss: UpdateAvailableEvent()
    data object Install: UpdateAvailableEvent()

    /** Opens the issue a changelog entry came from. */
    data class OpenIssue(val url: String): UpdateAvailableEvent()
}
