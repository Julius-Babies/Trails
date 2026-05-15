package es.jvbabi.trails.domain.usecase.auth

import es.jvbabi.trails.domain.repository.KeyValueRepository

class LoginUseCase(
    private val keyValueRepository: KeyValueRepository
) {
    suspend operator fun invoke(token: String, host: String) {
        keyValueRepository.setValue("trails.host", host)
        keyValueRepository.setValue("trails.token", token)
    }
}