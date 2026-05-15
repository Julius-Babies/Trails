package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Upsert
    suspend fun upsert(location: DbLocation)

    @Query("SELECT * FROM location ORDER BY timestamp DESC LIMIT 1")
    fun getLastLocation(): Flow<DbLocation?>
}
