package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.embedded.EmbeddedActiveShare
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ActiveShareDao {
    @Upsert
    suspend fun upsert(activeShare: DbActiveShare)

    @Transaction
    @Query("SELECT * FROM active_shares")
    fun getActiveShares(): Flow<List<EmbeddedActiveShare>>

    @Query("DELETE FROM active_shares WHERE id = :shareId")
    suspend fun deleteById(shareId: Uuid)

    @Transaction
    @Query("SELECT * FROM active_shares WHERE id = :shareId")
    fun getActiveShareById(shareId: Uuid): Flow<EmbeddedActiveShare?>

    /** A list because the same device may be reachable through several redeemed links. */
    @Transaction
    @Query("SELECT * FROM active_shares WHERE device_id = :deviceId")
    fun getActiveSharesForDevice(deviceId: Uuid): Flow<List<EmbeddedActiveShare>>
}