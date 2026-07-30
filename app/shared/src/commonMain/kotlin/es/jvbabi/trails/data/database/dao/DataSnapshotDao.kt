package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbDataSnapshot
import es.jvbabi.trails.data.database.entity.embedded.EmbeddedDataSnapshot
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface DataSnapshotDao {
    @Upsert
    suspend fun upsert(snapshot: DbDataSnapshot)

    @Query("UPDATE data_snapshot SET timestamp = :newTimestamp WHERE device_id = :deviceId AND timestamp = :oldTimestamp")
    suspend fun updateTimestamp(deviceId: Uuid, oldTimestamp: Long, newTimestamp: Long)

    @Query("SELECT * FROM data_snapshot WHERE id = :id")
    fun getSnapshotById(id: Uuid): Flow<EmbeddedDataSnapshot?>

    @Transaction
    @Query("SELECT * FROM data_snapshot WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    fun getLastSnapshot(deviceId: Uuid): Flow<EmbeddedDataSnapshot?>

    @Transaction
    @Query(
        """
            SELECT * FROM data_snapshot
            WHERE device_id = :deviceId
                AND is_synced = 0
                AND timestamp < :maxTimestamp
                AND id NOT IN (:excludedIds)
            ORDER BY timestamp ASC
            LIMIT :limit
        """
    )
    suspend fun getUnsyncedSnapshots(
        deviceId: Uuid,
        maxTimestamp: Long,
        excludedIds: Collection<Uuid>,
        limit: Int,
    ): List<EmbeddedDataSnapshot>

    @Query("SELECT COUNT(*) FROM data_snapshot WHERE device_id = :deviceId AND is_synced = 0")
    fun getUnsyncedSnapshotCount(deviceId: Uuid): Flow<Int>
}
