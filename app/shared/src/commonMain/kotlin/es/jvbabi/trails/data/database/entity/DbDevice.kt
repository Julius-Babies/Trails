package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.uuid.Uuid

@Entity(
    tableName = "devices",
    primaryKeys = ["id"],
    indices = [
        Index("id", unique = true),
        Index("owner_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbUser::class,
            parentColumns = ["id"],
            childColumns = ["owner_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DbDevice(
    @ColumnInfo("id") val id: Uuid,
    @ColumnInfo("manufacturer") val manufacturer: String,
    @ColumnInfo("model") val model: String,
    @ColumnInfo("friendly_name") val friendlyName: String,
    @ColumnInfo("display_name") val displayName: String,
    @ColumnInfo("owner_id") val ownerId: Uuid,
)