package es.jvbabi.trails.domain.model

import co.touchlab.kermit.Logger

/**
 * Logger shared by the whole update flow — the version check, the download and the install.
 *
 * One tag rather than one per class on purpose. Whether an update prompt appears is decided across
 * a use case, a view model and a repository, and following that decision in a log is only useful if
 * the whole path comes out under a single filter:
 *
 * ```
 * adb logcat -s TrailsUpdate
 * ```
 */
internal val updateLogger = Logger.withTag(UPDATE_LOG_TAG)

private const val UPDATE_LOG_TAG = "TrailsUpdate"
