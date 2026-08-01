package es.jvbabi.trails.domain.usecase.app

import es.jvbabi.trails.domain.model.AppVersions
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository

class GetReleaseChangelogsUseCase(
    private val trailsAppRepository: TrailsAppRepository,
    private val applicationRepository: ApplicationRepository,
) {
    /**
     * Collects the changelog of every release the user has not seen yet: newer than the running
     * build, up to and including [upToVersion].
     *
     * Versions come newest first. Releases without a readable changelog are left out, so a release
     * from before changelogs existed does not show up as an empty section.
     *
     * Returns `null` when nothing could be fetched or nothing is left to show — this decorates an
     * update prompt and must never keep that prompt from appearing.
     */
    suspend operator fun invoke(upToVersion: String): Changelog? {
        val currentVersion = trailsAppRepository.getCurrentVersion()
        val releases = ignoreErrors { trailsAppRepository.getReleases() } ?: return null
        val language = applicationRepository.language

        val versions = releases
            // The API returns releases newest first, which is the order they are shown in.
            .filter { release ->
                AppVersions.isNewerThan(release.version, currentVersion) &&
                    AppVersions.isAtLeast(upToVersion, release.version)
            }
            .mapNotNull { release ->
                ignoreErrors { trailsAppRepository.getChangelog(release, language) }
            }

        return if (versions.isEmpty()) null else Changelog(versions = versions)
    }
}
