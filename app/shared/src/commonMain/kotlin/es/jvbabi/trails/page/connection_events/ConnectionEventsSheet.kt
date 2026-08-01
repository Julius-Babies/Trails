@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeApi::class)

package es.jvbabi.trails.page.connection_events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushpal.jetlime.ItemsList
import com.pushpal.jetlime.JetLimeColumn
import com.pushpal.jetlime.JetLimeEventDefaults
import com.pushpal.jetlime.JetLimeExtendedEvent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.ui.components.ProgressiveBlurScrim
import es.jvbabi.trails.ui.components.ScrimEdge
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.data.database.entity.ConnectionEvent
import es.jvbabi.trails.utils.formatDate
import es.jvbabi.trails.utils.formatTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Composable
fun ConnectionEventsSheet(
    server: String,
    onClose: () -> Unit,
) {

    val viewModel = koinViewModel<ConnectionEventsViewModel>()
    LaunchedEffect(server) { viewModel.init(server) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ConnectionEventsSheetComponent(
        state = state,
        onClose = onClose,
    )
}

@Composable
private fun ConnectionEventsSheetComponent(
    state: ConnectionEventsState,
    onClose: () -> Unit,
) {
    if (state.server == null) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onClose,
        contentWindowInsets = { WindowInsets() },
        sheetState = sheetState,
    ) {
        ConnectionEventsSheetContent(
            state = state
        )
    }
}

@Composable
fun ConnectionEventsSheetContent(
    state: ConnectionEventsState,
) {
    if (state.server == null) return

    val localDensity = LocalDensity.current
    val hazeState = rememberHazeState()

    var headerHeight by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxWidth()) {
        val backgroundColor = BottomSheetDefaults.ContainerColor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BottomSheetDefaults.ContainerColor)
                .hazeSource(hazeState)
        ) {
            JetLimeColumn(
                modifier = Modifier.fillMaxSize(),
                itemsList = ItemsList(state.events),
                key = { _, item -> item.id },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + headerHeight, bottom = 8.dp),
            ) { _, event, position ->
                JetLimeExtendedEvent(
                    style = JetLimeEventDefaults.eventStyle(
                        position = position,
                    ),
                    additionalContent = {
                        Text(
                            modifier = Modifier.padding(end = 4.dp),
                            text = formatDate(event.timestamp) + "\n" + formatTime(event.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End,
                        )
                    }
                ) {
                    Column(Modifier.padding(top = 4.dp)) {
                        when(event.data) {
                            is ConnectionEvent.Event.Connected -> Text(stringResource(Res.string.connection_events_connected))
                            is ConnectionEvent.Event.Disconnected -> Text(stringResource(Res.string.connection_events_disconnected))
                        }
                    }
                }
            }
        }

        ProgressiveBlurScrim(
            hazeState = hazeState,
            edge = ScrimEdge.Top,
            containerColor = backgroundColor,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .onSizeChanged { size -> headerHeight = with(localDensity) { size.height.toDp() } },
        ) {
            Text(
                text = stringResource(Res.string.connection_events_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            )
            Text(
                text = stringResource(
                    if (state.isConnected) Res.string.connection_events_subtitle_connected
                    else Res.string.connection_events_subtitle_disconnected,
                    state.server,
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
            )
        }

    }
}

@Composable
@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
private fun ConnectionEventsSheetPreview() {
    ConnectionEventsSheetComponent(
        state = ConnectionEventsState(
            server = "trailsdevelopment.jvbabi.es",
            events = listOf(
                ConnectionEvent(
                    id = Uuid.random(),
                    server = "trailsdevelopment.jvbabi.es",
                    timestamp = Clock.System.now() - 1.minutes,
                    data = ConnectionEvent.Event.Connected
                ),
                ConnectionEvent(
                    id = Uuid.random(),
                    server = "trailsdevelopment.jvbabi.es",
                    timestamp = Clock.System.now() - 30.seconds,
                    data = ConnectionEvent.Event.Disconnected
                ),
                ConnectionEvent(
                    id = Uuid.random(),
                    server = "trailsdevelopment.jvbabi.es",
                    timestamp = Clock.System.now() - 1.minutes,
                    data = ConnectionEvent.Event.Connected,
                ),
            ),
        ),
        onClose = {}
    )
}
