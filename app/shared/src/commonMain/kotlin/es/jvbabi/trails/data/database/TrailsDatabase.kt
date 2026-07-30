package es.jvbabi.trails.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import es.jvbabi.trails.data.database.converter.InstantConverter
import es.jvbabi.trails.data.database.converter.UuidConverter
import es.jvbabi.trails.data.database.dao.*
import es.jvbabi.trails.data.database.entity.*
import kotlin.uuid.Uuid

@Database(
    entities = [
        DbKeyValue::class,
        DbDataSnapshot::class,
        DbUser::class,
        DbDevice::class,
        DbActiveShare::class,
        DbConnectionEvent::class,
    ],
    version = 4,
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
                // Existing snapshots default to unsynced: a device that was offline before the
                // upgrade still has to push them. Snapshots the server already holds are
                // re-uploaded under a new ID — [Migration3to4] regenerates them — and the server
                // recognises them by (device, timestamp) and acknowledges them without storing
                // a duplicate.
                connection.execSQL(
                    """
                        ALTER TABLE data_snapshot
                        ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0
                    """
                )
            }
        }

        val Migration3to4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                        ALTER TABLE data_snapshot
                        ADD COLUMN id TEXT NOT NULL DEFAULT ''
                    """
                )

                val rowIds = mutableListOf<Long>()
                connection.prepare("SELECT rowid FROM data_snapshot").use { statement ->
                    while (statement.step()) rowIds.add(statement.getLong(0))
                }

                connection.prepare("UPDATE data_snapshot SET id = ? WHERE rowid = ?").use { statement ->
                    rowIds.forEach { rowId ->
                        statement.bindText(1, Uuid.random().toString())
                        statement.bindLong(2, rowId)
                        statement.step()
                        statement.reset()
                    }
                }

                // The primary key changes, so the table has to be recreated.
                connection.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS `data_snapshot_new` (
                            `id` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `device_id` TEXT NOT NULL,
                            `latitude` REAL NOT NULL,
                            `longitude` REAL NOT NULL,
                            `bearing` REAL NOT NULL,
                            `bearing_accuracy` REAL,
                            `location_accuracy` REAL NOT NULL,
                            `battery_level` REAL,
                            `battery_charging` INTEGER,
                            `is_synced` INTEGER NOT NULL,
                            PRIMARY KEY(`id`, `timestamp`, `device_id`),
                            FOREIGN KEY(`device_id`) REFERENCES `devices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """
                )
                connection.execSQL(
                    """
                        INSERT INTO `data_snapshot_new` (
                            `id`, `timestamp`, `device_id`, `latitude`, `longitude`, `bearing`,
                            `bearing_accuracy`, `location_accuracy`, `battery_level`, `battery_charging`, `is_synced`
                        )
                        SELECT
                            `id`, `timestamp`, `device_id`, `latitude`, `longitude`, `bearing`,
                            `bearing_accuracy`, `location_accuracy`, `battery_level`, `battery_charging`, `is_synced`
                        FROM `data_snapshot`
                    """
                )
                connection.execSQL("DROP TABLE `data_snapshot`")
                connection.execSQL("ALTER TABLE `data_snapshot_new` RENAME TO `data_snapshot`")
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_data_snapshot_id` ON `data_snapshot` (`id`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_data_snapshot_device_id` ON `data_snapshot` (`device_id`)")
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<TrailsDatabase> {
    override fun initialize(): TrailsDatabase
}
