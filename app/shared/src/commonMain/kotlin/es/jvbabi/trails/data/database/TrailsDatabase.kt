package es.jvbabi.trails.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import es.jvbabi.trails.data.database.converter.InstantConverter
import es.jvbabi.trails.data.database.converter.UuidConverter
import es.jvbabi.trails.data.database.dao.*
import es.jvbabi.trails.data.database.entity.*

@Database(
    entities = [
        DbKeyValue::class,
        DbDataSnapshot::class,
        DbUser::class,
        DbDevice::class,
        DbActiveShare::class,
        DbConnectionEvent::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
@TypeConverters(
    UuidConverter::class,
    InstantConverter::class,
)
abstract class TrailsDatabase : RoomDatabase() {
    abstract val keyValueDao: KeyValueDao
    abstract val dataSnapshotDao: DataSnapshotDao
    abstract val userDao: UserDao
    abstract val deviceDao: DeviceDao
    abstract val activeShareDao: ActiveShareDao
    abstract val connectionEventDao: ConnectionEventDao

    companion object {
        val Migration2to3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                        ALTER TABLE data_snapshot
                        ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0
                    """
                )
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<TrailsDatabase> {
    override fun initialize(): TrailsDatabase
}
