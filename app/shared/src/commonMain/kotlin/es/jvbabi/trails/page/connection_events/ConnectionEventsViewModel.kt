@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.connection_events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.data.database.entity.ConnectionEvent
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionEventsViewModel(
    private val trailsServerRepository: TrailsServerRepository,
    private val keyValueRepository: KeyValueRepository,
) : ViewModel() {
    val state: StateFlow<ConnectionEventsState>
        field = MutableStateFlow(ConnectionEventsState())

    fun init(server: String) {
        state.update { it.copy(server = server) }
    }

    init {
        viewModelScope.launch(CoroutineName("Connection events")) {
            state
                .distinctUntilChangedBy { it.server }
                .filter { it.server != null }
                .collectLatest { stateSnapshot ->
                    coroutineScope {
                        launch {
                            trailsServerRepository.getConnectionEvents(stateSnapshot.server!!)
                                .collectLatest { events ->
                                    state.update { it.copy(events = events) }
                                }
                        }

                        launch {
                            combine(
                                state.map { it.server }.distinctUntilChanged().filterNotNull(),
                                keyValueRepository.get("trails.host").flatMapLatest { homeserver ->
                                    if (homeserver == state.value.server && state.value.server != null) trailsServerRepository.isConnected
                                    else trailsServerRepository.isServerConnected(state.value.server!!)
                                }
                            ) { _, isConnected ->
                                isConnected
                            }.collectLatest { connected ->
                                state.update { it.copy(isConnected = connected) }
                            }
                        }
                    }
                }
        }
    }
}

data class ConnectionEventsState(
    val server: String? = null,
    val isConnected: Boolean = false,
    val events: List<ConnectionEvent> = emptyList(),
)