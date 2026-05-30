package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ApplicationRepositoryImpl(
    private val isVisibleStateFlow: StateFlow<Boolean>,
) : ApplicationRepository {

    companion object {
        const val KOIN_KEY_APP_IN_FOREGROUND_FLOW = "app_in_foreground_flow"
    }

    override fun getApplicationForegroundState(): Flow<Boolean> {
        return isVisibleStateFlow
    }
}