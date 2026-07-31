package es.jvbabi.trails.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Instant

// A fresh formatter per call: it captures the locale at creation, and the app locale can change
// while the process lives.
actual fun formatDate(instant: Instant): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(instant.toNSDate())

actual fun formatTime(instant: Instant): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterMediumStyle
    }.stringFromDate(instant.toNSDate())

private fun Instant.toNSDate(): NSDate =
    NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)
