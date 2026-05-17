package es.jvbabi.trails.domain.repository

import es.jvbabi.trails.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface UserRepository {
    fun getUser(id: Uuid): Flow<User?>
}