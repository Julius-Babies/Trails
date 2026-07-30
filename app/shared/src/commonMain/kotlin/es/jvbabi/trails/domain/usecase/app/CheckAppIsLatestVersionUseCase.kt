package es.jvbabi.trails.domain.usecase.app

import es.jvbabi.trails.BuildKonfig
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

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

        if (isAtLeast(currentVersion, latestVersion)) return AppVersionState.IsLatest

        return AppVersionState.UpdateAvailable(
            version = latestVersion,
            downloadLink = ignoreErrors { trailsAppRepository.getDownloadLinkForLatestVersion() },
        )
    }

    /**
     * Whether [currentVersion] is the same as or newer than [latestVersion].
     *
     * Version names are build timestamps (`yyyyMMdd_HHmm`), so they can be ordered
     * chronologically. Anything that doesn't match that shape (e.g. a locally overridden
     * `BUILD_TAG`) can only be compared for equality.
     */
    private fun isAtLeast(currentVersion: String, latestVersion: String): Boolean {
        val current = parseVersionTimestamp(currentVersion)
        val latest = parseVersionTimestamp(latestVersion)
        if (current != null && latest != null) return current >= latest
        return currentVersion == latestVersion
    }

    private fun parseVersionTimestamp(version: String): LocalDateTime? {
        if (!TIMESTAMP_VERSION_REGEX.matches(version)) return null
        return try {
            LocalDateTime.parse(version, TIMESTAMP_VERSION_FORMAT)
        } catch (_: Exception) {
            null
        }
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

private val TIMESTAMP_VERSION_REGEX = Regex("^\\d{8}_\\d{4}$")

private val TIMESTAMP_VERSION_FORMAT = LocalDateTime.Format {
    year()
    monthNumber(Padding.ZERO)
    day(Padding.ZERO)
    char('_')
    hour(Padding.ZERO)
    minute(Padding.ZERO)
}

/**
 * Runs [block] and swallows failures. Update checks are strictly best-effort: an unreachable
 * GitHub must never surface as an error to the user.
 */
private suspend fun <T> ignoreErrors(block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
