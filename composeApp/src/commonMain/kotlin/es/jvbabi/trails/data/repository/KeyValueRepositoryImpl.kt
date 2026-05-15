package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbKeyValue
import es.jvbabi.trails.domain.repository.KeyValueRepository

class KeyValueRepositoryImpl(
    private val database: TrailsDatabase
): KeyValueRepository {
    override suspend fun setValue(key: String, value: String) {
        database.keyValueDao.upsert(DbKeyValue(key, value))
    }
}
