package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbKeyValue
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyValueDao {

    @Upsert
    suspend fun upsert(keyValue: DbKeyValue)

    @Query("SELECT value FROM key_value WHERE `key` = :key")
    fun getValue(key: String): Flow<String?>
}