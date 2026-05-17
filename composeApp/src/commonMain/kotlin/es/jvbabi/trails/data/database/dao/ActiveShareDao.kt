package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbActiveShare

@Dao
interface ActiveShareDao {
    @Upsert
    suspend fun upsert(activeShare: DbActiveShare)
}