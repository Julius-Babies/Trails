package es.jvbabi.trails.utils

import java.text.DateFormat
import java.util.Date
import kotlin.time.Instant

// Resolved per call rather than cached: the formatters capture the default locale at creation, and
// the app locale can change while the process lives (e.g. per-app language settings).
actual fun formatDate(instant: Instant): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(instant.toEpochMilliseconds()))

actual fun formatTime(instant: Instant): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(instant.toEpochMilliseconds()))
