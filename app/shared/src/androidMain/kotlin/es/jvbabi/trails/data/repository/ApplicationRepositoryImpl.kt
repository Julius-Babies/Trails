package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class ApplicationRepositoryImpl: ApplicationRepository, KoinComponent {

    companion object {
        const val KOIN_KEY_APP_IN_FOREGROUND_FLOW = "app_in_foreground_flow"
    }

    private val isVisibleStateFlow by inject<StateFlow<Boolean>>(named(KOIN_KEY_APP_IN_FOREGROUND_FLOW))

    override fun getApplicationForegroundState(): Flow<Boolean> {
        return isVisibleStateFlow
    }
}