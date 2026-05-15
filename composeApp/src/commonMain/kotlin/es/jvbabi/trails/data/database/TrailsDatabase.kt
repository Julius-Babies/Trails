package es.jvbabi.trails.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.dao.KeyValueDao
import es.jvbabi.trails.data.database.entity.DbKeyValue

@Database(
    entities = [
        DbKeyValue::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TrailsDatabase: RoomDatabase() {
    abstract val keyValueDao: KeyValueDao
}