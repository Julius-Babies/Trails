package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface SnapshotRepository {
    fun startSnapshotCollection(scope: CoroutineScope)

    fun getSnapshotById(id: Uuid): Flow<Snapshot?>
    suspend fun storeSnapshot(snapshot: Snapshot)

    fun getCurrentSnapshotForDevice(device: Device): Flow<Snapshot?>
}
