package es.jvbabi.trails.page.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.page.home.components.CardSheetValue
import es.jvbabi.trails.page.home.components.DraggableCardSheet
import es.jvbabi.trails.page.home.components.Map
import es.jvbabi.trails.page.home.components.NavigationBar
import es.jvbabi.trails.page.home.components.rememberDraggableCardSheetState
import es.jvbabi.trails.page.shares.main.SharesScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.settings

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
}

@Composable
fun HomeContent(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onEvent: (event: HomeEvent) -> Unit,
) {
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val collapsedHeight = 72.dp
        val draggableCardSheetState = rememberDraggableCardSheetState(
            expandedHeight = maxHeight,
            semiExpandedHeight = 350.dp,
            collapsedHeight = collapsedHeight,
            initialValue = CardSheetValue.Collapsed,
        )
        DraggableCardSheet(
            modifier = Modifier.fillMaxSize(),
            state = draggableCardSheetState,
            content = {
                Scaffold { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {

                        Box(Modifier.fillMaxSize())
                        Map(
                            state = state,
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

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isConnectedToServer) Color.Green else Color.Red)
                                )

                                Text(
                                    text = if (state.isConnectedToServer) "Verbunden mit Trails Server" else "Nicht verbunden mit Trails Server",
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
                                blurRadius = 4.dp + draggableCardSheetState.progress * 4.dp
                                style = hazeStyle
                            }
                        }
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = draggableCardSheetState.progress))
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

                    val defaultContentBottomPadding = contentPadding.bottom * draggableCardSheetState.collapsedProgress + collapsedHeight

                    Box(
                        modifier = Modifier
                            .padding(bottom = max(defaultContentBottomPadding, WindowInsets.ime.asPaddingValues().calculateBottomPadding()))
                            .fillMaxSize()
                            .defaultMinSize(minHeight = collapsedHeight)
                            .clipToBounds()
                            .bottomFadeOut(active = draggableCardSheetState.isUserDragging && draggableCardSheetState.progress < 0.5f)
                    ) {
                        AnimatedContent(
                            targetState = state.selectedTab,
                        ) { selectedTab ->
                            when (selectedTab) {
                                HomeState.Tab.MyDevices -> Text("Hier könnten deine Geräte sein!")
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
                            .padding(bottom = contentPadding.bottom * draggableCardSheetState.collapsedProgress)
                            .fillMaxWidth()
                            .height(collapsedHeight - 8.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NavigationBar(
                                selectedTab = state.selectedTab,
                                draggableCardSheetState = draggableCardSheetState,
                                onSelect = { onEvent(HomeEvent.SelectTab(it)) }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeContent(
        state = HomeState(
            ownLocation = null,
            isConnectedToServer = true,
        ),
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