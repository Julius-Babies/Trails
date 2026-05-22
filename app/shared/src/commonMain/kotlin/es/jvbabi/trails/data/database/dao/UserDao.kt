package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbUser
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: DbUser)

    @Query("SELECT * FROM users WHERE id = :id")
    fun getById(id: Uuid): Flow<DbUser?>
}