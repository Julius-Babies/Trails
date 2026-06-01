package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

interface KeyValueRepository {
    fun <T> get(key: Key<T>): Flow<T?>
    suspend fun <T> set(key: Key<T>, value: T)
    suspend fun <T> delete(key: Key<T>)
}

sealed class Key<VALUE>(val key: String) {
    abstract fun toValue(value: String): VALUE
    abstract fun fromValue(value: VALUE): String

    abstract class SerializableKey<T>(
        key: String,
        private val serializer: KSerializer<T>
    ) : Key<T>(key) {
        override fun toValue(value: String): T = Json.decodeFromString(serializer, value)
        override fun fromValue(value: T): String = Json.encodeToString(serializer, value)
    }

    abstract class StringKey(key: String) : Key<String>(key) {
        override fun toValue(value: String): String = value
        override fun fromValue(value: String): String = value
    }

    abstract class UuidKey(key: String): Key<Uuid>(key) {
        override fun toValue(value: String): Uuid = Uuid.parse(value)
        override fun fromValue(value: Uuid): String = value.toString()
    }

    data object ThisDeviceId: UuidKey("trails.thisDeviceId")
    data object UserId: UuidKey("trails.userId")
    data object Host: StringKey("trails.host")
    data object Token: StringKey("trails.token")

    data object Theme: Key<es.jvbabi.trails.domain.repository.Theme>("app.theme") {
        override fun fromValue(value: es.jvbabi.trails.domain.repository.Theme): String = value.name
        override fun toValue(value: String): es.jvbabi.trails.domain.repository.Theme = es.jvbabi.trails.domain.repository.Theme.valueOf(value)
    }
}

enum class Theme {
    Light, Dark, System
}