package es.jvbabi.trails.domain.extension

import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.first

class Settings(
    private val keyValueRepository: KeyValueRepository,
) {
    suspend fun getHomeserver(): URLBuilder {
        return keyValueRepository.get(Key.Host).first()?.let { URLBuilder("https://$it") }
            ?: throw IllegalStateException("No server URL available")
    }
}