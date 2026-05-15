package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbLocation

@Dao
interface LocationDao {
    @Upsert
    suspend fun upsert(location: DbLocation)
}
