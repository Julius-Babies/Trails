package es.jvbabi.trails.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import es.jvbabi.trails.data.database.entity.DbDevice
import es.jvbabi.trails.data.database.entity.embedded.EmbeddedDevice
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface DeviceDao {
    @Upsert
    suspend fun upsertDevices(device: List<DbDevice>)

    @Query("SELECT * FROM devices WHERE owner_id = :ownerId")
    fun getDevicesByOwner(ownerId: Uuid): Flow<List<EmbeddedDevice>>

    @Query("SELECT * FROM devices")
    fun getDevices(): Flow<List<EmbeddedDevice>>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    fun getDeviceById(deviceId: Uuid): Flow<EmbeddedDevice?>

    @Query("DELETE FROM devices WHERE id IN (:deviceIds)")
    suspend fun deleteDevicesByIds(deviceIds: List<Uuid>)
}