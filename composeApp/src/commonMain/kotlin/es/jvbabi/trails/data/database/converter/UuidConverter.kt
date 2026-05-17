package es.jvbabi.trails.data.database.converter

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlin.uuid.Uuid

@ProvidedTypeConverter
class UuidConverter {
    @TypeConverter
    fun fromUuid(uuid: Uuid): String = uuid.toString()

    @TypeConverter
    fun toUuid(uuid: String): Uuid = Uuid.parse(uuid)
}