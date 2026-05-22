package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import es.jvbabi.trails.domain.model.User
import kotlin.uuid.Uuid

@Entity(
    tableName = "users",
    primaryKeys = ["id"],
)
data class DbUser(
    @ColumnInfo("id") val id: Uuid,
    @ColumnInfo("homeserver") val homeserver: String,
    @ColumnInfo("username") val username: String,
) {
    fun toModel() = User(
        id = id,
        homeserver = homeserver,
        username = username,
    )
}