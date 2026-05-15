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
    @ColumnInfo("timestamp") val timestamp: Long,
)