package es.jvbabi.trails.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.dao.KeyValueDao
import es.jvbabi.trails.data.database.dao.LocationDao
import es.jvbabi.trails.data.database.entity.DbKeyValue
import es.jvbabi.trails.data.database.entity.DbLocation

@Database(
    entities = [
        DbKeyValue::class,
        DbLocation::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TrailsDatabase: RoomDatabase() {
    abstract val keyValueDao: KeyValueDao
    abstract val locationDao: LocationDao
}