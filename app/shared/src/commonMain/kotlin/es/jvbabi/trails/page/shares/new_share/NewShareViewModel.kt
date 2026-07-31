@file:OptIn(ExperimentalCoroutinesApi::class)

package es.jvbabi.trails.page.shares.new_share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.ShareCreationResult
import es.jvbabi.trails.domain.repository.ShareRepository
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import trails.app.shared.generated.resources.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlin.uuid.Uuid

class NewShareViewModel(
    private val keyValueRepository: KeyValueRepository,
    private val devicesRepository: DevicesRepository,
    private val fileRepository: FileRepository,
    private val shareRepository: ShareRepository,
): ViewModel() {
    val state: StateFlow<NewShareState>
        field = MutableStateFlow(NewShareState())

    init {
        viewModelScope.launch {
            keyValueRepository
                .get(Key.ThisDeviceId)
                .filterNotNull()
                .flatMapLatest { devicesRepository.getDeviceById(it) }
                .filterNotNull()
                .collectLatest { device ->
                    state.value = state.value.copy(
                        currentDevice = device,
                    )

                    // Wait for the device image to be available
                    devicesRepository.hasDeviceImage(device).filter { it }.first()
                    state.value = state.value.copy(
                        image = fileRepository.readFile(devicesRepository.getFileNameForDeviceImage(device))
                    )
                }
        }
    }

    fun onEvent(event: NewShareEvent) {
        when (event) {
            is NewShareEvent.LocationShareHistoryStateChanged -> {
                state.value = state.value.copy(
                    selectedLocationShareHistoryState = event.newState,
                )
            }
            NewShareEvent.ShareBatteryLevelChanged -> {
                state.value = state.value.copy(
                    shareBatteryLevel = !state.value.shareBatteryLevel,
                )
            }
            is NewShareEvent.ShareNameChanged -> {
                state.value = state.value.copy(
                    shareName = event.newName,
                    showShareNameEmptyError = false,
                    showShareNameAlreadyUsedError = false,
                )
            }
            NewShareEvent.AllowMultiuseLinkChanged -> {
                state.value = state.value.copy(
                    allowMultiuseLink = !state.value.allowMultiuseLink,
                )
            }
            NewShareEvent.CreateShareClicked -> {
                viewModelScope.launch(Dispatchers.IO + CoroutineName("CreateShare")) {
                    if (state.value.shareName.isBlank()) {
                        state.value = state.value.copy(
                            showShareNameEmptyError = true,
                        )
                        return@launch
                    }

                    state.value = state.value.copy(
                        shareCreationState = NewShareState.ShareCreationState.Loading,
                        showShareNameAlreadyUsedError = false,
                    )

                    try {
                        val result = shareRepository.createShare(
                            locationHistory = state.value.selectedLocationShareHistoryState.duration,
                            withBatteryState = state.value.shareBatteryLevel,
                            shareName = state.value.shareName,
                            allowMultiuse = state.value.allowMultiuseLink,
                        )
                        state.value = when (result) {
                            is ShareCreationResult.Success -> state.value.copy(
                                shareCreationState = NewShareState.ShareCreationState.Success(
                                    shareId = result.shareId,
                                    homeserver = result.homeServer,
                                    username = state.value.currentDevice?.owner?.username,
                                ),
                            )
                            // A taken name is an input error: report it on the text field
                            // instead of pushing the user into the error state.
                            is ShareCreationResult.Error.ShareNameAlreadyExists -> state.value.copy(
                                shareCreationState = NewShareState.ShareCreationState.Idle,
                                showShareNameAlreadyUsedError = true,
                            )
                            is ShareCreationResult.Error.OtherError -> state.value.copy(
                                shareCreationState = NewShareState.ShareCreationState.Error(result.errorMessage),
                            )
                        }
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to create share" }
                        state.value = state.value.copy(
                            shareCreationState = NewShareState.ShareCreationState.Error(e.message ?: getString(Res.string.common_unknown_error)),
                        )
                    }
                }
            }
            NewShareEvent.ResetShareCreationState -> {
                state.value = state.value.copy(
                    shareCreationState = NewShareState.ShareCreationState.Idle,
                )
            }
            NewShareEvent.ResetInputFields -> {
                state.value = state.value.copy(
                    selectedLocationShareHistoryState = NewShareState.LocationShareHistoryState.OneHour,
                    shareBatteryLevel = true,
                    shareName = "",
                    showShareNameAlreadyUsedError = false,
                    allowMultiuseLink = false,
                )
            }
        }
    }
}

data class NewShareState(
    val image: ByteArray? = null,
    val currentDevice: Device? = null,
    val selectedLocationShareHistoryState: LocationShareHistoryState = LocationShareHistoryState.OneHour,
    val shareBatteryLevel: Boolean = true,
    val shareName: String = "",
    val showShareNameEmptyError: Boolean = false,
    val showShareNameAlreadyUsedError: Boolean = false,
    val allowMultiuseLink: Boolean = false,
    val shareCreationState: ShareCreationState = ShareCreationState.Idle,
) {
    enum class LocationShareHistoryState(val duration: Duration) {
        NoHistory(0.seconds),
        OneHour(1.hours),
        SixHours(6.hours),
        OneDay(24.hours),
        OneWeek(7 * 24.hours),
        Infinite(Duration.INFINITE),
    }

    sealed class ShareCreationState {
        object Idle: ShareCreationState()
        object Loading: ShareCreationState()
        /**
         * @param username the user the share belongs to, `null` when it is not known. The share
         *   sheet's title is built from it where the strings are available, not here.
         */
        class Success(val shareId: Uuid, val homeserver: String, val username: String?): ShareCreationState() {
            val url = URLBuilder().apply {
                protocol = URLProtocol("trailsapp", -1)
                host = "application"
                appendPathSegments(homeserver)
                appendPathSegments("share", shareId.toHexString())
            }
        }
        data class Error(val errorMessage: String): ShareCreationState()
    }
}

sealed class NewShareEvent {
    data class LocationShareHistoryStateChanged(val newState: NewShareState.LocationShareHistoryState): NewShareEvent()
    data object ShareBatteryLevelChanged: NewShareEvent()
    data class ShareNameChanged(val newName: String): NewShareEvent()
    data object AllowMultiuseLinkChanged: NewShareEvent()
    data object CreateShareClicked: NewShareEvent()
    data object ResetShareCreationState: NewShareEvent()
    data object ResetInputFields: NewShareEvent()
}