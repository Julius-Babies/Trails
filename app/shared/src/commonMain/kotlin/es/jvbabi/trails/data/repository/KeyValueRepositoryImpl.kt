package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.entity.DbKeyValue
import es.jvbabi.trails.domain.repository.KeyValueRepository
import kotlinx.coroutines.flow.Flow

class KeyValueRepositoryImpl(
    private val database: TrailsDatabase
): KeyValueRepository {
    override suspend fun setValue(key: String, value: String) {
        database.keyValueDao.upsert(DbKeyValue(key, value))
    }

    override suspend fun delete(key: String) {
        database.keyValueDao.delete(key)
    }

    override fun get(key: String): Flow<String?> {
        return database.keyValueDao.getValue(key)
    }
}
