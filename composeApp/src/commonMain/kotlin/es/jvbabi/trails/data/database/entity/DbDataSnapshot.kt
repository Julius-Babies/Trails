package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.uuid.Uuid

@Entity(
    tableName = "data_snapshot",
    primaryKeys = ["timestamp", "device_id"],
    indices = [
        Index("device_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbDevice::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DbDataSnapshot(
    @ColumnInfo("timestamp") val timestamp: Long,
    @ColumnInfo("device_id") val deviceId: Uuid,
    @ColumnInfo("latitude") val latitude: Double,
    @ColumnInfo("longitude") val longitude: Double,
    @ColumnInfo("bearing") val bearing: Float,
    @ColumnInfo("bearing_accuracy") val bearingAccuracy: Float?,
    @ColumnInfo("location_accuracy") val locationAccuracy: Float,
    @ColumnInfo("battery_level") val batteryLevel: Float?,
    @ColumnInfo("battery_charging") val batteryCharging: Boolean?,
)