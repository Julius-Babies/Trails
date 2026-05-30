package es.jvbabi.trails.page.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.page.devices.main.DevicesTab
import es.jvbabi.trails.page.home.components.*
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.shares.main.SharesScreen
import es.jvbabi.trails.utils.IntPaddingValues
import es.jvbabi.trails.utils.blendColor
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.locate_fixed
import trails.app.shared.generated.resources.maximize
import trails.app.shared.generated.resources.settings
import kotlin.uuid.Uuid

const val DEBUG = false

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {}
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onOpenSettings = onOpenSettings,
        onEvent = viewModel::onEvent,
    )

    val localDensity = LocalDensity.current
    LaunchedEffect(localDensity.density) {
        viewModel.setup(
            localDensity = localDensity.density
        )
    }

    if (DEBUG) {
        val mapContentPadding by viewModel.mapContentPadding.collectAsStateWithLifecycle()
        if (mapContentPadding != null) {
            val dpPadding = with(localDensity) {
                PaddingValues(
                    top = mapContentPadding!!.top.toDp(),
                    start = mapContentPadding!!.start.toDp(),
                    end = mapContentPadding!!.end.toDp(),
                    bottom = mapContentPadding!!.bottom.toDp(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dpPadding)
                    .border(1.dp, Color.Red)
            )
        }
    }
}

@Composable
fun HomeContent(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onEvent: (event: HomeEvent) -> Unit,
) {
    val cardCollapsedHeight = 72.dp
    var fabHeight by remember { mutableStateOf(48.dp) }

    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onEvent(HomeEvent.OnViewportResize(size))
            }
    ) {
        val draggableCardSheetState = rememberDraggableCardSheetState(
            expandedHeight = maxHeight,
            semiExpandedHeight = 350.dp,
            collapsedHeight = cardCollapsedHeight,
            initialValue = CardSheetValue.Collapsed,
        )
        DraggableCardSheet(
            modifier = Modifier.fillMaxSize(),
            state = draggableCardSheetState,
            content = {
                val density = LocalDensity.current

                Scaffold { scaffoldPadding ->
                    val contentPadding = draggableCardSheetState.backgroundContentPaddingValues + PaddingValues(top = scaffoldPadding.calculateTopPadding())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {
                        Box(Modifier.fillMaxSize())
                        Map(
                            state = state,
                            onDeviceClick = { device ->
                                Logger.i { "Map device clicked: ${device.device.displayName}" }
                            },
                            onUserDragStart = { onEvent(HomeEvent.UserDragged) },
                        )

                        Box(
                            modifier = Modifier
                                .padding(contentPadding)
                                .fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                FilledTonalIconButton(
                                    onClick = onOpenSettings,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.settings),
                                        contentDescription = "Settings"
                                    )
                                }
                            }

                            val trackingLabel = when (state.trackingMode) {
                                HomeState.TrackingMode.None -> "Übersicht"
                                HomeState.TrackingMode.Overview -> "Mein Standort"
                                HomeState.TrackingMode.OwnLocation -> "Übersicht"
                            }

                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { size ->
                                        fabHeight = with(density) { size.height.toDp() }
                                    }
                                    .padding(bottom = 8.dp),
                            ) {
                                ExtendedFloatingActionButton(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    text = {
                                        AnimatedContent(
                                            targetState = trackingLabel
                                        ) { label ->
                                            Text(label)
                                        }
                                    },
                                    icon = {
                                        AnimatedContent(
                                            targetState = state.trackingMode,
                                            modifier = Modifier.size(24.dp),
                                        ) { currentTrackingMode ->
                                            Icon(
                                                painter = painterResource(when (currentTrackingMode) {
                                                    HomeState.TrackingMode.None, HomeState.TrackingMode.OwnLocation -> Res.drawable.maximize
                                                    HomeState.TrackingMode.Overview -> Res.drawable.locate_fixed
                                                }),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    onClick = { onEvent(HomeEvent.ToggleTrackingMode) },
                                )
                            }
                        }
                    }
                }
            },
            cardContent = { contentPadding ->
                val hazeStyle = HazeMaterials.thin()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeEffect(hazeState) {
                            blurEffect {
                                blurRadius = 8.dp + draggableCardSheetState.progress * 4.dp
                                style = hazeStyle
                            }
                        }
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = draggableCardSheetState.progress/(1/0.8f) + 0.8f))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = (animateDpAsState(if (draggableCardSheetState.isUserDragging) 5.dp else 4.dp).value + ((contentPadding.top - 8.dp) * draggableCardSheetState.expandedProgress)).coerceAtLeast(0.dp))
                            .align(Alignment.TopCenter)
                            .width(animateDpAsState(if (draggableCardSheetState.isUserDragging) 52.dp else 40.dp).value)
                            .height(animateDpAsState(if (draggableCardSheetState.isUserDragging) 2.dp else 4.dp).value)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    val defaultContentBottomPadding = cardCollapsedHeight + 8.dp + 16.dp * draggableCardSheetState.progress

                    Box(
                        modifier = Modifier
                            .padding(bottom = max(defaultContentBottomPadding, WindowInsets.ime.asPaddingValues().calculateBottomPadding()))
                            .fillMaxSize()
                            .clipToBounds()
                            .bottomFadeOut(active = draggableCardSheetState.isUserDragging && draggableCardSheetState.expandedProgress == 0.0f)
                    ) {
                        if (!LocalInspectionMode.current) AnimatedContent(
                            targetState = state.selectedTab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) }
                        ) { selectedTab ->
                            when (selectedTab) {
                                HomeState.Tab.MyDevices -> DevicesTab(
                                    contentPadding = contentPadding,
                                    nestedScrollConnection = draggableCardSheetState.nestedScrollConnection,
                                    onFocusDevice = { deviceId ->
                                        onEvent(HomeEvent.FocusDevice(deviceId))
                                        if (deviceId != null && draggableCardSheetState.targetValue == CardSheetValue.Expanded) {
                                            scope.launch { draggableCardSheetState.semiExpand() }
                                        }
                                    }
                                )
                                HomeState.Tab.Things -> Text("Hier könnten deine Gegenstände sein!")
                                HomeState.Tab.Shares -> SharesScreen(
                                    nestedScrollConnection = draggableCardSheetState.nestedScrollConnection,
                                    onExpandCard = { scope.launch { draggableCardSheetState.expand() } },
                                    onSemiExpandCard = { scope.launch { draggableCardSheetState.semiExpand() } },
                                    contentPadding = contentPadding,
                                )
                            }

                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(blendColor(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainer, draggableCardSheetState.progress/2f).copy(alpha = draggableCardSheetState.progress))
                            .padding(bottom = 16.dp * draggableCardSheetState.progress)
                            .fillMaxWidth()
                            .height(cardCollapsedHeight + 8.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        HorizontalDivider(Modifier.alpha(draggableCardSheetState.progress))
                        NavigationBar(
                            modifier = Modifier.weight(1f),
                            selectedTab = state.selectedTab,
                            draggableCardSheetState = draggableCardSheetState,
                            onSelect = { onEvent(HomeEvent.SelectTab(it)) }
                        )
                    }
                }
            }
        )

        val density = LocalDensity.current
        val windowInsets = WindowInsets.safeContent.asPaddingValues()
        val intPaddingValues by remember(draggableCardSheetState.targetValue, fabHeight) {
            mutableStateOf(with(density) {
                IntPaddingValues(
                    top = windowInsets.calculateTopPadding().roundToPx(),
                    bottom = (draggableCardSheetState.targetBackgroundContentPaddingValues.bottom.coerceAtMost(draggableCardSheetState.semiContainerPadding + draggableCardSheetState.semiExpandedHeight) + fabHeight).roundToPx(),
                    start = 16.dp.roundToPx(),
                    end = 16.dp.roundToPx(),
                ) + IntPaddingValues(16.dp.roundToPx())
            })
        }

        LaunchedEffect(intPaddingValues) {
            onEvent(HomeEvent.OnMapContentAreaPadding(intPaddingValues))
        }
    }
}

@Composable
internal fun rememberMockHomeState(): HomeState {
    var deviceImage by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(Unit) {
        deviceImage = Res.readBytes("files/device-sample.jpg")
    }

    val now = LocalDateTime(2026, Month.MAY, 17, 15, 0, 0)
    val mockDevice = Device(
        id = Uuid.random(),
        manufacturer = "Google",
        model = "Pixel 7",
        friendlyName = "Pixel 7",
        displayName = "Julius' Pixel 7",
        owner = User(
            id = Uuid.random(),
            homeserver = "trails.jvbabi.es",
            username = "julius",
        ),
        batteryState = Device.BatteryState.Shared(percentage = 85, isCharging = false),
    )
    val mockLocation = Location(
        latitude = 40.4168,
        longitude = -3.7038,
        bearing = 45f,
        bearingAccuracy = 10f,
        locationAccuracy = 20f,
        time = now,
    )
    val mockSnapshot = Snapshot(
        device = mockDevice,
        time = now,
        location = mockLocation,
        batteryState = es.jvbabi.trails.domain.repository.BatteryState(percentage = 72, isCharging = true),
    )

    return HomeState(
        ownLocation = Location(
            latitude = 40.4178,
            longitude = -3.7030,
            bearing = 0f,
            bearingAccuracy = null,
            locationAccuracy = 15f,
            time = now,
        ),
        devices = listOf(
            HomeState.HomeDevice(
                device = mockDevice,
                image = deviceImage,
                snapshot = mockSnapshot,
            ),
        ),
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
fun HomeScreenPreview() {
    HomeContent(
        state = rememberMockHomeState(),
        onOpenSettings = {},
        onEvent = {},
    )
}

fun Modifier.bottomFadeOut(
    height: Dp = 48.dp,
    active: Boolean = true,
) = composed {

    val fadeAlpha by animateFloatAsState(
        targetValue = if (active) 0f else 1f
    )

    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeHeightPx = height.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = fadeAlpha)),
                    startY = size.height - fadeHeightPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
