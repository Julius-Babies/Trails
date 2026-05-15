package util.date

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

operator fun LocalDateTime.plus(duration: Duration): LocalDateTime {
    val instant = toInstant(TimeZone.UTC)
    return instant.plus(duration).toLocalDateTime(TimeZone.UTC)
}

operator fun LocalTime.plus(duration: Duration): LocalTime {
    val instant = atDate(LocalDate.fromEpochDays(0)).toInstant(TimeZone.UTC)
    return instant.plus(duration).toLocalDateTime(TimeZone.UTC).time
}

infix operator fun LocalDate.plus(duration: Duration): LocalDate {
    return this.plus(duration.inWholeDays, DateTimeUnit.DAY)
}