package es.jvbabi.trails.domain.usecase.communication

import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.flow.first

class StartExternalConnectionsUseCase(
    private val shareRepository: ShareRepository,
    private val trailsServerRepository: TrailsServerRepository,
    private val keyValueRepository: KeyValueRepository,
) {
    suspend operator fun invoke() {
        val homeserver = keyValueRepository.get("trails.host").first()
        val shares = shareRepository.getShares().first()

        val activeHosts = shares
            .groupBy { it.device.owner.homeserver }
            .filter { it.key != homeserver }

        activeHosts.forEach { (host, _) ->
            trailsServerRepository.connectWithOtherServer(host)
        }
    }
}