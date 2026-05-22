package es.jvbabi.trails.page.shares.add_share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UseShareLinkResult
import io.ktor.http.URLBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class AddShareViewModel(
    private val trailsServerRepository: TrailsServerRepository,
): ViewModel() {

    val state: StateFlow<AddShareState>
        field = MutableStateFlow(AddShareState())


    fun onEvent(event: AddShareEvent) {
        when (event) {
            is AddShareEvent.UrlChanged -> {
                state.update { it.copy(url = event.url) }
            }

            is AddShareEvent.AddShare -> viewModelScope.launch {
                if (state.value.isLoading) return@launch
                try {
                    state.update { it.copy(isLoading = true) }
                    val url = URLBuilder(state.value.url)
                    val hostname = url.pathSegments[1]
                    require(url.pathSegments[2] == "share")
                    val id = url.pathSegments[3]
                    when (trailsServerRepository.useShareLink(hostname, id)) {
                        is UseShareLinkResult.Success -> state.update { it.copy(success = true) }
                        is UseShareLinkResult.Used -> state.update { it.copy(success = true) }
                        is UseShareLinkResult.NotExisting -> state.update { it.copy(success = false) }
                        is UseShareLinkResult.Error -> state.update { it.copy(success = false) }
                    }
                } finally {
                    state.update { it.copy(isLoading = false) }
                }
            }
            is AddShareEvent.Clear -> {
                state.update { AddShareState() }
            }
        }
    }
}

data class AddShareState(
    val url: String = "",
    val isLoading: Boolean = false,
    val success: Boolean = false,
)

sealed class AddShareEvent {
    data class UrlChanged(val url: String): AddShareEvent()
    data object AddShare: AddShareEvent()
    data object Clear: AddShareEvent()
}