package es.jvbabi.trails.data.database.converter

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlin.time.Instant

@ProvidedTypeConverter
class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}