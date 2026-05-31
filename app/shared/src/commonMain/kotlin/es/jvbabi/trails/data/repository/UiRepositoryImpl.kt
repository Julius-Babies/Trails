package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.Snackbar
import es.jvbabi.trails.domain.repository.UiRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

class UiRepositoryImpl: UiRepository {

    override val currentSnackbar: StateFlow<Snackbar?>
        field = MutableStateFlow<Snackbar?>(null)

    private val coroutineScope = CoroutineScope(Dispatchers.Main + CoroutineName("SnackbarQueue"))
    private val queue = Channel<Snackbar>(capacity = Channel.UNLIMITED)

    init {
        coroutineScope.launch {
            queue
                .receiveAsFlow()
                .collect {
                    currentSnackbar.value = it.copy(shownAt = Clock.System.now())
                    delay(it.autoDismiss)
                    currentSnackbar.value = null
                }
        }
    }

    override fun sendSnackbar(title: String, autoDismiss: Duration) {
        val snackbar = Snackbar(
            title = title,
            autoDismiss = autoDismiss,
            createdAt = Clock.System.now()
        )

        coroutineScope.launch { queue.send(snackbar) }
    }
}