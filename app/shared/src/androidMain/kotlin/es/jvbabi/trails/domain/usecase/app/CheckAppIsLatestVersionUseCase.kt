package es.jvbabi.trails.domain.usecase.app

import es.jvbabi.trails.BuildKonfig
import es.jvbabi.trails.domain.model.AppVersions
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository

class CheckAppIsLatestVersionUseCase(
    private val trailsAppRepository: TrailsAppRepository,
    private val applicationRepository: ApplicationRepository,
) {
    /**
     * Compares the running build against the latest published release.
     *
     * Returns `null` when the check couldn't be completed (offline, rate limited, unexpected
     * response, …) — the caller cannot tell anything about the version then, so the state stays
     * unknown instead of claiming the app is up to date. Debug builds return `null` as well
     * unless `app.check_for_updates.enable_in_debug` is set in `local.properties`.
     */
    suspend operator fun invoke(): AppVersionState? {
        if (applicationRepository.isDebugBuild && !BuildKonfig.CHECK_FOR_UPDATES_IN_DEBUG) return null

        val latestVersion = ignoreErrors { trailsAppRepository.getLatestVersion() } ?: return null
        val currentVersion = trailsAppRepository.getCurrentVersion()

        if (AppVersions.isAtLeast(currentVersion, latestVersion)) return AppVersionState.IsLatest

        return AppVersionState.UpdateAvailable(
            version = latestVersion,
            downloadLink = ignoreErrors { trailsAppRepository.getDownloadLinkForLatestVersion() },
        )
    }
}

sealed class AppVersionState {
    /** The running build is the latest release — or newer, as local builds are. */
    data object IsLatest : AppVersionState()

    /**
     * A newer release exists. [downloadLink] is the universal APK of that release and is
     * `null` when the release has no such asset (e.g. iOS-only releases).
     */
    data class UpdateAvailable(val version: String, val downloadLink: String?) : AppVersionState()
}
