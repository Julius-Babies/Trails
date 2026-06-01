package es.jvbabi.trails.page.devices.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.page.connection_events.ConnectionEventsSheet
import es.jvbabi.trails.page.devices.Screen
import es.jvbabi.trails.page.devices.device.DeviceScreen
import es.jvbabi.trails.page.devices.main.components.DeviceCard
import es.jvbabi.trails.ui.components.*
import es.jvbabi.trails.utils.PaddingValues
import es.jvbabi.trails.utils.padding
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun DevicesTab(
    contentPadding: es.jvbabi.trails.utils.PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    initialRoute: Screen,
    onFocusDevice: (deviceId: Uuid?) -> Unit,
) {
    val topBarState = remember { TopBarState() }
    val backstack = remember { mutableStateListOf(initialRoute) }
    LaunchedEffect(backstack.lastOrNull()) {
        val lastBackstackEntry = backstack.lastOrNull()
        if (lastBackstackEntry !is Screen.Device) onFocusDevice(null)
        else onFocusDevice(lastBackstackEntry.deviceId)
    }

    val hazeState = rememberHazeState()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        CompositionLocalProvider(LocalTopBar provides topBarState) {
            key(contentPadding) {
                Box(Modifier.fillMaxSize()) {
                    NavDisplay(
                        modifier = Modifier.fillMaxSize(),
                        backStack = backstack.ifEmpty { listOf(Screen.Main) },
                        onBack = { backstack.removeLastOrNull() },
                        transitionSpec = {
                            val animSpec = tween<IntOffset>(durationMillis = 380, easing = FastOutSlowInEasing)
                            slideInHorizontally(animSpec) { it } togetherWith
                                    slideOutHorizontally(animSpec) { -it / 5 }
                        },
                        popTransitionSpec = {
                            val animSpec = tween<IntOffset>(durationMillis = 380, easing = FastOutSlowInEasing)
                            slideInHorizontally(animSpec) { -it / 5 } togetherWith
                                    slideOutHorizontally(animSpec) { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                        },
                        entryProvider = { key ->
                            return@NavDisplay when (key) {
                                is Screen.Main -> NavEntry(key = key) {
                                    DevicesScreen(
                                        contentPadding = contentPadding,
                                        backstack = backstack,
                                        nestedScrollConnection = nestedScrollConnection,
                                    )
                                }

                                is Screen.Device -> NavEntry(key = key) {
                                    DeviceScreen(
                                        contentPadding = contentPadding,
                                        deviceId = key.deviceId,
                                        backstack = backstack,
                                        nestedScrollConnection = nestedScrollConnection,
                                    )
                                }
                            }
                        }
                    )

                    TopBar(
                        state = topBarState,
                        modifier = Modifier
                            .padding(top = contentPadding.top)
                            .padding(horizontal = 8.dp)
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
fun DevicesScreen(
    contentPadding: PaddingValues,
    backstack: MutableList<Screen>,
    nestedScrollConnection: NestedScrollConnection,
) {

    val viewModel = koinViewModel<DevicesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ConfigureTopBar(
        title = "Geräte",
        subtitle = "${state.myDevices.size + state.foreignDevices.size} Geräte"
    )

    DevicesContent(
        contentPadding = contentPadding,
        nestedScrollConnection = nestedScrollConnection,
        state = state,
        onClickDevice = { backstack.add(Screen.Device(it.id)) },
    )
}

@Composable
fun DevicesContent(
    contentPadding: es.jvbabi.trails.utils.PaddingValues,
    state: DevicesState,
    nestedScrollConnection: NestedScrollConnection?,
    onClickDevice: (device: Device) -> Unit,
) {

    var showConnectionEventsForServer by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .hazeSource(LocalHazeState.current)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding.copy(bottom = 0.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (nestedScrollConnection == null) it else it.nestedScroll(nestedScrollConnection) }
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = contentPadding.bottom)
            ) {
                if (state.myDevices.isNotEmpty()) {
                    Text(
                        text = "Meine Geräte",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp, top = 24.dp)
                    )

                    state.myDevices.forEach { myDevice ->
                        DeviceCard(
                            isThisDevice = state.thisDevice?.device?.id == myDevice.device.id,
                            modifier = Modifier.fillMaxWidth(),
                            device = myDevice,
                            onClick = { onClickDevice(myDevice.device) }
                        )
                    }
                }

                if (state.foreignDevices.isNotEmpty()) {
                    Text(
                        text = "Mit mir geteilte Geräte",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    state.foreignDevices.forEach { foreignDevice ->
                        DeviceCard(
                            isThisDevice = state.thisDevice?.device?.id == foreignDevice.device.id,
                            modifier = Modifier.fillMaxWidth(),
                            device = foreignDevice,
                            onClick = { onClickDevice(foreignDevice.device) }
                        )
                    }
                }
            }
        }

        if (state.isConnectedToHomeServer != null) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .dropShadow(
                    RoundedCornerShape(8.dp),
                    shadow = Shadow(
                        radius = 16.dp,
                        offset = DpOffset(0.dp, 4.dp),
                        color = Color.Black.copy(alpha = 0.2f)
                    )
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable { showConnectionEventsForServer = state.thisDevice?.device?.owner?.homeserver }
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (state.isConnectedToHomeServer is DevicesState.HomeServerConnectionState.Connected) Color.Green else Color.Red)
            )

            Text(
                text = if (state.isConnectedToHomeServer is DevicesState.HomeServerConnectionState.Connected) "Verbunden mit Trails Server" else "Nicht verbunden mit Trails Server",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    if (showConnectionEventsForServer != null) ConnectionEventsSheet(
        server = showConnectionEventsForServer!!,
        onClose = { showConnectionEventsForServer = null }
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun DevicesPreview() {
    DevicesContent(
        contentPadding = es.jvbabi.trails.utils.PaddingValues(),
        state = DevicesState(

        ),
        nestedScrollConnection = null,
        onClickDevice = {},
    )
}

