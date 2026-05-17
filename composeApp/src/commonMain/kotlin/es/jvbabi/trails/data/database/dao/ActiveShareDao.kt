package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbActiveShare
import es.jvbabi.trails.data.database.entity.embedded.EmbeddedActiveShare
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveShareDao {
    @Upsert
    suspend fun upsert(activeShare: DbActiveShare)

    @Query("SELECT * FROM active_shares")
    fun getActiveShares(): Flow<List<EmbeddedActiveShare>>
}