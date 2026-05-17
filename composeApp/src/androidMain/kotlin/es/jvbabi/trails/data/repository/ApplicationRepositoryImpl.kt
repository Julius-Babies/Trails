package es.jvbabi.trails.data.repository

import es.jvbabi.trails.MainActivity
import es.jvbabi.trails.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent

class ApplicationRepositoryImpl: ApplicationRepository, KoinComponent {
    override fun getApplicationForegroundState(): Flow<Boolean> {
        return MainActivity.isVisible
    }
}