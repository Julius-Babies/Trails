package es.jvbabi.trails.ui.overlay.update_available

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.model.UpdateDownload
import es.jvbabi.trails.domain.model.UpdateDownloadTarget
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
     * Fetches the update into [target], keeping the state in step with how far it has got.
     *
     * A download already in flight is left to finish: pressing Install twice must not pull the same
     * APK down twice.
     */
    private fun startDownload(target: UpdateDownloadTarget) {
        if (state.value?.download is UpdateDownload.Running) return
        val downloadLink = state.value?.downloadLink ?: return

        viewModelScope.launch {
            updateRepository.downloadUpdate(url = downloadLink, target = target)
                .collect { download ->
                    state.update { it?.copy(download = download, downloadTarget = target) }

                    // Only what was fetched to be installed goes on to the installer. A download in
                    // the user's Downloads folder is theirs to install and ends here.
                    if (download is UpdateDownload.Done && target == UpdateDownloadTarget.AppCache) {
                        install(download.uri)
                    }
                }
        }
    }

    /**
     * Hands a finished download to the system installer.
     *
     * The permission is looked at again rather than taken on trust from before the download: it
     * lives in the system settings, and a download takes long enough for it to have been withdrawn
     * in the meantime. If it has been, the dialog goes back up — with the APK already downloaded, so
     * granting it carries straight on to the install.
     */
    private fun install(uri: Uri) {
        if (!updateRepository.canInstallUpdates()) {
            state.update { it?.copy(isInstallPermissionRequired = true) }
            return
        }

        updateRepository.installUpdate(uri)
    }

    /**
     * Puts the update in the user's Downloads folder, for them to install themselves.
     *
     * Takes the permission dialog down with it: this is the way out of that dialog which does not
     * need the permission at all.
     */
    private fun installManually() {
        state.update { it?.copy(isInstallPermissionRequired = false) }
        startDownload(UpdateDownloadTarget.Downloads)
    }

    /**
     * Carries on with the install once the permission has been granted.
     *
     * Finding the permission there takes the dialog down and starts the download the user asked for
     * in the first place — they already pressed Install, and making them press it again would be
     * asking twice for the same thing.
     *
     * A permission that is still missing leaves the dialog up, which covers the user who went to the
     * settings and came back without flipping the switch.
     */
    private fun continueOnceInstallPermissionGranted() {
        val current = state.value ?: return
        if (!current.isInstallPermissionRequired) return
        if (!updateRepository.canInstallUpdates()) return

        state.update { it?.copy(isInstallPermissionRequired = false) }

        // An APK that is already in the cache is installed rather than fetched all over again —
        // that is the case where the permission fell away while it was downloading.
        val downloaded = (current.download as? UpdateDownload.Done)
            ?.takeIf { current.downloadTarget == UpdateDownloadTarget.AppCache }

        if (downloaded != null) {
            updateRepository.installUpdate(downloaded.uri)
        } else {
            startDownload(UpdateDownloadTarget.AppCache)
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
                // Asked on every press rather than once when the prompt goes up: the permission
                // lives in system settings and can be taken away again in the meantime.
                if (!updateRepository.canInstallUpdates()) {
                    state.update { it?.copy(isInstallPermissionRequired = true) }
                    return
                }

                startDownload(UpdateDownloadTarget.AppCache)
            }

            // Deliberately leaves the dialog up: the user is off to the settings and may well come
            // back without having granted anything, in which case the dialog is still what they
            // need to see. It is taken down by dismissInstallPermissionIfGranted() on return.
            is UpdateAvailableEvent.GrantInstallPermission ->
                updateRepository.openInstallPermissionSettings()

            is UpdateAvailableEvent.RecheckInstallPermission ->
                continueOnceInstallPermissionGranted()

            is UpdateAvailableEvent.InstallManually -> installManually()

            // TODO: remember the choice, so Install goes straight to the Downloads folder from now
            //  on and the permission is never asked for again (#29). Until then this is the
            //  one-off above.
            is UpdateAvailableEvent.AlwaysInstallManually -> installManually()

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

    /** The download in flight or the one that has just ended, and `null` before any was started. */
    val download: UpdateDownload? = null,

    /** What [download] was fetched for, so a finished one is put to the use it was meant for. */
    val downloadTarget: UpdateDownloadTarget? = null,

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
