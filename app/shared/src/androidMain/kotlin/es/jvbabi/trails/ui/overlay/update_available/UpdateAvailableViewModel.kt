package es.jvbabi.trails.ui.overlay.update_available

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import es.jvbabi.trails.domain.repository.UpdateRepository
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
    private val updateRepository: UpdateRepository,
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
     * Takes the permission dialog down once the permission it asks for has been granted.
     *
     * A dialog whose permission is still missing stays up, which covers the user who went to the
     * settings and came back without flipping the switch.
     */
    private fun dismissInstallPermissionIfGranted() {
        if (state.value?.isInstallPermissionRequired != true) return
        if (!updateRepository.canInstallUpdates()) return
        state.update { it?.copy(isInstallPermissionRequired = false) }
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
                // Asked on every press rather than once when the prompt goes up: the permission
                // lives in system settings and can be taken away again in the meantime.
                if (!updateRepository.canInstallUpdates()) {
                    state.update { it?.copy(isInstallPermissionRequired = true) }
                    return
                }

                val downloadLink = state.value?.downloadLink ?: return
                openUrl(downloadLink)
                state.update { it?.copy(isDismissed = true) }
            }

            // Deliberately leaves the dialog up: the user is off to the settings and may well come
            // back without having granted anything, in which case the dialog is still what they
            // need to see. It is taken down by dismissInstallPermissionIfGranted() on return.
            is UpdateAvailableEvent.GrantInstallPermission ->
                updateRepository.openInstallPermissionSettings()

            is UpdateAvailableEvent.RecheckInstallPermission -> dismissInstallPermissionIfGranted()

            // TODO: hand the download off so the user installs it themselves (#29).
            is UpdateAvailableEvent.InstallManually -> Unit

            // TODO: remember the choice, so Install goes straight to the download from now on and
            //  the permission is never asked for again (#29).
            is UpdateAvailableEvent.AlwaysInstallManually -> Unit

            is UpdateAvailableEvent.DismissInstallPermission ->
                state.update { it?.copy(isInstallPermissionRequired = false) }

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
     * Whether the app has been asked to install the update but is not allowed to, which is what
     * the permission dialog is put up for.
     */
    val isInstallPermissionRequired: Boolean = false,

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

    /** Sends the user to the settings where the install permission is granted. */
    data object GrantInstallPermission: UpdateAvailableEvent()

    /**
     * Ask whether the install permission has been granted in the meantime.
     *
     * Granting happens in the system settings, and Android reports nothing back when it is done.
     * The app returning to the front is the only signal there is, so the overlay sends this then.
     */
    data object RecheckInstallPermission: UpdateAvailableEvent()

    /** Install this one update by hand instead of granting the permission. */
    data object InstallManually: UpdateAvailableEvent()

    /** Install by hand from now on, without being asked for the permission again. */
    data object AlwaysInstallManually: UpdateAvailableEvent()

    /** The user closed the permission dialog without choosing any of it. */
    data object DismissInstallPermission: UpdateAvailableEvent()

    /** Opens the issue a changelog entry came from. */
    data class OpenIssue(val url: String): UpdateAvailableEvent()
}
