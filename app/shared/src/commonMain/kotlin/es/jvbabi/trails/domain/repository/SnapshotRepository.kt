package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SnapshotRepository {
    fun startSnapshotCollection(scope: CoroutineScope)

    fun getSnapshotById(id: Uuid): Flow<Snapshot?>
    suspend fun storeSnapshot(snapshot: Snapshot)

    /**
     * Snapshots of [deviceId] that the server has not acknowledged yet and that are older than
     * [olderThan], oldest first. [excludedIds] skips snapshots whose acknowledgement is still
     * outstanding, so a caller polling repeatedly does not upload them twice.
     */
    suspend fun getUnsyncedSnapshots(
        deviceId: Uuid,
        olderThan: Instant,
        excludedIds: Collection<Uuid>,
        limit: Int,
    ): List<Snapshot>

    /**
     * Number of snapshots of [deviceId] the server has not acknowledged yet. The flow re-emits
     * whenever snapshots are stored or acknowledged.
     */
    fun getUnsyncedSnapshotCount(deviceId: Uuid): Flow<Int>

    fun getCurrentSnapshotForDevice(device: Device): Flow<Snapshot?>
}
