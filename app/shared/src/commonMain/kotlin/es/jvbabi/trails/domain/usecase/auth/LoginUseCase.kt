package es.jvbabi.trails.domain.usecase.auth

import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository

class LoginUseCase(
    private val keyValueRepository: KeyValueRepository,
    private val trailsServerRepository: TrailsServerRepository,
) {
    suspend operator fun invoke(token: String, host: String) {
        keyValueRepository.setValue("trails.host", host)
        keyValueRepository.setValue("trails.token", token)

        trailsServerRepository.getMeData()
        trailsServerRepository.updateUserDevices()
    }
}