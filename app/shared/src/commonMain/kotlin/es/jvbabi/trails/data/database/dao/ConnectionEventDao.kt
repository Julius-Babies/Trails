package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbConnectionEvent
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ConnectionEventDao {

    @Upsert
    suspend fun upsert(event: DbConnectionEvent)

    @Query("SELECT * FROM connection_events WHERE server = :server ORDER BY timestamp DESC")
    fun getEvents(server: String): Flow<List<DbConnectionEvent>>

    @Query("DELETE FROM connection_events WHERE id = :id")
    suspend fun delete(id: Uuid)
}
