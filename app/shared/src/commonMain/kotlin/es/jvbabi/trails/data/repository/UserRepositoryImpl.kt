package es.jvbabi.trails.data.repository

import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class UserRepositoryImpl(
    private val database: TrailsDatabase,
): UserRepository {
    override fun getUser(id: Uuid): Flow<User?> {
        return database.userDao.getById(id).map { it?.toModel() }
    }
}