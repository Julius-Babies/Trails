package es.jvbabi.trails.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import es.jvbabi.trails.data.database.converter.UuidConverter
import es.jvbabi.trails.data.database.dao.ActiveShareDao
import es.jvbabi.trails.data.database.dao.DeviceDao
import es.jvbabi.trails.data.database.dao.KeyValueDao
import es.jvbabi.trails.data.database.dao.DataSnapshotDao
import es.jvbabi.trails.data.database.dao.UserDao
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.DbKeyValue
import es.jvbabi.trails.data.database.entity.DbDataSnapshot
import es.jvbabi.trails.data.database.entity.DbUser

@Database(
    entities = [
        DbKeyValue::class,
        DbDataSnapshot::class,
        DbUser::class,
        DbDevice::class,
        DbActiveShare::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    UuidConverter::class,
)
abstract class TrailsDatabase: RoomDatabase() {
    abstract val keyValueDao: KeyValueDao
    abstract val dataSnapshotDao: DataSnapshotDao
    abstract val userDao: UserDao
    abstract val deviceDao: DeviceDao
    abstract val activeShareDao: ActiveShareDao
}