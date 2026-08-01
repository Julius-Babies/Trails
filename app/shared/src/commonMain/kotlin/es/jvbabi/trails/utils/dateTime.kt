package es.jvbabi.trails.utils

import kotlin.time.Instant

/**
 * Locale-aware date and time formatting.
 *
 * Never build a date or time from a hand-written pattern: the order of the parts, the separators and
 * the 12/24-hour clock all differ per locale. These delegate to the platform formatters, which
 * follow the locale the OS (and therefore the app) currently runs in.
 */

/** [instant] as a date in the user's locale, without a time, e.g. `1 Aug 2026` or `01.08.2026`. */
expect fun formatDate(instant: Instant): String

/** [instant] as a time in the user's locale, without a date, e.g. `2:30:05 PM` or `14:30:05`. */
expect fun formatTime(instant: Instant): String
