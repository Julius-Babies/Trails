package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.uuid.Uuid

@Entity(
    tableName = "active_shares",
    primaryKeys = ["id"],
    indices = [
        Index("id", unique = true),
        Index("device_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbDevice::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class DbActiveShare(
    @ColumnInfo("id") val id: Uuid,
    @ColumnInfo("device_id") val deviceId: Uuid,
)