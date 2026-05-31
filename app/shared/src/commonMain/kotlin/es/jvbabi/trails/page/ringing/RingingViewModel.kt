package es.jvbabi.trails.page.ringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.jvbabi.trails.domain.repository.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RingingViewModel(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RingingState())
    val state: StateFlow<RingingState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var onStop: (() -> Unit)? = null
    private var stopped = false

    init {
        viewModelScope.launch {
            deviceRepository.ringStopReceived.collect {
                if (!stopped) {
                    stopped = true
                    _state.update { it.copy(isRinging = false) }
                    onStop?.invoke()
                }
            }
        }
    }

    fun init(deviceName: String, onStop: () -> Unit) {
        _state.update { it.copy(searchedByDeviceName = deviceName) }
        this.onStop = onStop
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startMark = kotlin.time.TimeSource.Monotonic.markNow()
            while (isActive) {
                val elapsed = startMark.elapsedNow().inWholeSeconds
                _state.update { it.copy(elapsedSeconds = elapsed) }
                delay(1000)
            }
        }
    }

    fun onEvent(event: RingingEvent) {
        when (event) {
            is RingingEvent.Stop -> {
                deviceRepository.stopRinging()
                if (!stopped) {
                    stopped = true
                    _state.update { it.copy(isRinging = false) }
                    onStop?.invoke()
                }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

data class RingingState(
    val isRinging: Boolean = true,
    val elapsedSeconds: Long = 0,
    val searchedByDeviceName: String = "",
)

sealed class RingingEvent {
    data object Stop : RingingEvent()
}
