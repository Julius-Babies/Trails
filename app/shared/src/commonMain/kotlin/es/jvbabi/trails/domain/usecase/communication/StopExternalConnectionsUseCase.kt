package es.jvbabi.trails.domain.usecase.communication

import es.jvbabi.trails.domain.repository.TrailsServerRepository

class StopExternalConnectionsUseCase(
    private val trailsServerRepository: TrailsServerRepository,
) {
    suspend operator fun invoke() {
        trailsServerRepository.stopAllOtherServerConnections()
    }
}