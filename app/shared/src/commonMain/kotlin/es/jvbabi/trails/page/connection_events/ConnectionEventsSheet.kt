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
import androidx.compose.ui.graphics.Brush
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
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.data.database.entity.ConnectionEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
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
        val hazeStyle = HazeMaterials.thin(containerColor = backgroundColor)
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
                            text = buildString {
                                val localDateTime = event.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                                appendLine(localDateTime.format(LocalDateTime.Format {
                                    year()
                                    char('-')
                                    monthNumber(Padding.ZERO)
                                    char('-')
                                    day(Padding.ZERO)
                                }))
                                appendLine(localDateTime.format(LocalDateTime.Format {
                                    hour(Padding.ZERO)
                                    char(':')
                                    minute(Padding.ZERO)
                                    char(':')
                                    second(Padding.ZERO)
                                }))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End,
                        )
                    }
                ) {
                    Column(Modifier.padding(top = 4.dp)) {
                        when(event.data) {
                            is ConnectionEvent.Event.Connected -> Text("Verbunden")
                            is ConnectionEvent.Event.Disconnected -> Text("Getrennt")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .onSizeChanged{ size -> headerHeight = with(localDensity) { size.height.toDp() } }
                .hazeEffect(hazeState) {
                    blurEffect {
                        blurRadius = 24.dp
                        style = hazeStyle
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f,
                        )
                    }
                }
                .background(Brush.verticalGradient(0.25f to backgroundColor.copy(alpha = 1f), 1f to backgroundColor.copy(alpha = 0f)))
        ) {
            Text(
                text = "Verbindungsereignisse",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            )
            Text(
                text = buildString {
                    append(state.server)
                    append(" - ")
                    if (state.isConnected) append("verbunden")
                    else append("nicht verbunden")
                },
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
