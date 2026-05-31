package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

interface UiRepository {

    val currentSnackbar: StateFlow<Snackbar?>

    fun sendSnackbar(title: String, autoDismiss: Duration)
}

data class Snackbar(
    val title: String,
    val createdAt: Instant,
    val shownAt: Instant? = null,
    val autoDismiss: Duration = 5.seconds,
    val action: (() -> Unit)? = null,
)