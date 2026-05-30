package es.jvbabi.trails.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

@Entity(
    tableName = "connection_events",
    primaryKeys = ["id"],
    indices = [Index("id", unique = true)],
)
data class DbConnectionEvent(
    @ColumnInfo("id") val id: Uuid,
    @ColumnInfo("server") val server: String,
    @ColumnInfo("timestamp") val timestamp: Instant,
    @ColumnInfo("data") val data: String,
) {
    fun toModel(): ConnectionEvent = ConnectionEvent(
        id = id,
        server = server,
        timestamp = timestamp,
        data = json.decodeFromString(data)
    )
}

data class ConnectionEvent(
    val id: Uuid,
    val server: String,
    val timestamp: Instant,
    val data: Event
) {
    @Serializable
    sealed class Event {
        @Serializable data object Connected: Event()
        @Serializable data object Disconnected: Event()
    }

    fun toEntity(): DbConnectionEvent = DbConnectionEvent(
        id = id,
        server = server,
        timestamp = timestamp,
        data = json.encodeToString(data)
    )
}
