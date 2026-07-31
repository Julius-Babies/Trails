package es.jvbabi.trails.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.domain.usecase.SetupNotificationsUseCase
import es.jvbabi.trails.page.devices.Screen
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class HomeViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val backgroundServiceRepository: BackgroundServiceRepository,
    private val trailsServerRepository: TrailsServerRepository,
    private val setupNotificationsUseCase: SetupNotificationsUseCase,
) : ViewModel() {

    val state: StateFlow<HomeState>
        field = MutableStateFlow(HomeState())

    /**
     * Requests that the card sheet moves to its semi expanded position. The sheet state lives in
     * the composition, so the request is emitted rather than stored.
     */
    val semiExpandSheet = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch(CoroutineName("Start service if user exists + update user data")) {
            val doesUserExist = keyValueRepository.get(Key.UserId).first() != null
            if (!doesUserExist) return@launch

            setupNotificationsUseCase()

            val sessionHealth = trailsServerRepository.checkSessionHealth()
            if (sessionHealth is SessionHealthState.InvalidOrExpired || sessionHealth is SessionHealthState.NoSessionExpected) return@launch
            if (sessionHealth is SessionHealthState.Error) {
                Logger.e { "Failed to get session health: ${sessionHealth.errorMessage}" }
                return@launch
            }

            trailsServerRepository.getMeData()
            trailsServerRepository.updateUserDevices()
            trailsServerRepository.syncAccountShares()
            trailsServerRepository.pruneRemovedShares()
            backgroundServiceRepository.startService()
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectTab -> state.update { it.copy(selectedTab = event.tab) }
            is HomeEvent.SelectDeviceOnMap -> {
                state.update { it.copy(selectedTab = HomeState.Tab.MyDevices(Screen.Device(event.deviceId))) }
                semiExpandSheet.tryEmit(Unit)
            }
        }
    }
}

data class HomeState(
    val selectedTab: Tab = Tab.MyDevices(Screen.Main),
) {
    sealed class Tab {
        data class MyDevices(val initialRoute: Screen): Tab()
        data object Things: Tab()
        data object Shares: Tab()
    }

    data class HomeDevice(
        val device: Device,
        val image: ByteArray?,
        val snapshot: Snapshot?,
    )
}

sealed class HomeEvent {
    data class SelectTab(val tab: HomeState.Tab) : HomeEvent()

    /** A marker on the map was tapped: open that device's detail route and reveal the sheet. */
    data class SelectDeviceOnMap(val deviceId: Uuid) : HomeEvent()
}
