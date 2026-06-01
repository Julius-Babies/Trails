package es.jvbabi.trails.domain.usecase.auth

import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository

class LoginUseCase(
    private val keyValueRepository: KeyValueRepository,
    private val trailsServerRepository: TrailsServerRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository
) {
    suspend operator fun invoke(token: String, host: String) {
        keyValueRepository.set(Key.Host, host)
        keyValueRepository.set(Key.Token, token)

        trailsServerRepository.getMeData()
        trailsServerRepository.updateUserDevices()
        backgroundServiceRepository.startService()
    }
}