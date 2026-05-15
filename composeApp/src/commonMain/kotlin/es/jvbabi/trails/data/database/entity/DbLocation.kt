package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "location",
    primaryKeys = ["timestamp"]
)
data class DbLocation(
    @ColumnInfo("latitude") val latitude: Double,
    @ColumnInfo("longitude") val longitude: Double,
    @ColumnInfo("bearing") val bearing: Float,
    @ColumnInfo("bearing_accuracy") val bearingAccuracy: Float?,
    @ColumnInfo("location_accuracy") val locationAccuracy: Float,
    @ColumnInfo("battery_level") val batteryLevel: Float?,
    @ColumnInfo("timestamp") val timestamp: Long,
)