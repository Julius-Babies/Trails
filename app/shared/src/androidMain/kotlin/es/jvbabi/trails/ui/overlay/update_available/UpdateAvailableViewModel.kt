package es.jvbabi.trails.ui.overlay.update_available

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import es.jvbabi.trails.domain.usecase.app.AppVersionState
import es.jvbabi.trails.domain.usecase.app.CheckAppIsLatestVersionUseCase
import es.jvbabi.trails.domain.usecase.app.GetReleaseChangelogsUseCase
import es.jvbabi.trails.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateAvailableViewModel(
    private val trailsAppRepository: TrailsAppRepository,
    private val applicationRepository: ApplicationRepository,
    private val checkAppIsLatestVersionUseCase: CheckAppIsLatestVersionUseCase,
    private val getReleaseChangelogsUseCase: GetReleaseChangelogsUseCase,
) : ViewModel() {
    val state: StateFlow<UpdateAvailableState?>
        field = MutableStateFlow(null)

    /**
     * The release the user last said "not now" to.
     *
     * Kept so the recurring check does not ask about the same release again every time the app is
     * brought back to the front — but a release published after that decision is worth a new prompt.
     */
    private var dismissedVersion: String? = null

    init {
        viewModelScope.launch {
            // The foreground state emits its current value right away, so this covers the check on
            // start as well as every return to the front. Collected rather than collected-latest:
            // a check in flight is cheap and finishing it beats restarting it.
            applicationRepository.getApplicationForegroundState()
                .distinctUntilChanged()
                .collect { isInForeground -> if (isInForeground) checkForUpdate() }
        }
    }

    /**
     * Looks for a newer release and, if there is one the user has not already waved off, puts the
     * overlay up.
     *
     * A check that finds nothing never takes an overlay down again: it may have failed (offline,
     * rate limited), and pulling the prompt out from under the user would be worse than leaving it.
     */
    private suspend fun checkForUpdate() {
        // Anything but a confirmed newer release (including a failed check) leaves the state as it
        // is, which keeps the overlay hidden.
        val updateAvailable = checkAppIsLatestVersionUseCase() as? AppVersionState.UpdateAvailable
            ?: return
        if (updateAvailable.version == dismissedVersion) return

        // Already prompting for exactly this release — restarting would throw away a changelog that
        // has arrived in the meantime.
        if (state.value?.latestVersion == updateAvailable.version) return

        state.value = UpdateAvailableState(
            currentVersion = trailsAppRepository.getCurrentVersion(),
            latestVersion = updateAvailable.version,
            downloadLink = updateAvailable.downloadLink,
        )

        // Fetched only after the overlay is already showing: it takes one request per release, and
        // the update prompt must not wait for it. Releases already read are served from the
        // repository's cache, so a later check only pays for what is new.
        val changelog = getReleaseChangelogsUseCase(upToVersion = updateAvailable.version)
        state.update { it?.copy(changelog = changelog, areChangelogsLoading = false) }
    }

    fun onEvent(event: UpdateAvailableEvent) {
        when (event) {
            is UpdateAvailableEvent.RequestDismiss -> state.update { it?.copy(isDismissed = true) }
            is UpdateAvailableEvent.Install -> {
                val downloadLink = state.value?.downloadLink ?: return
                openUrl(downloadLink)
                state.update { it?.copy(isDismissed = true) }
            }
            // Leaves the overlay open: the user is looking something up, not done updating.
            is UpdateAvailableEvent.OpenIssue -> openUrl(event.url)
            is UpdateAvailableEvent.Dismissed -> {
                dismissedVersion = state.value?.latestVersion
                state.value = null
            }
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
    /** The user wants the overlay gone; it still has to animate itself out. */
    data object RequestDismiss: UpdateAvailableEvent()

    /** The overlay has finished animating out and can leave the composition. */
    data object Dismissed: UpdateAvailableEvent()

    data object Install: UpdateAvailableEvent()

    /** Opens the issue a changelog entry came from. */
    data class OpenIssue(val url: String): UpdateAvailableEvent()
}
