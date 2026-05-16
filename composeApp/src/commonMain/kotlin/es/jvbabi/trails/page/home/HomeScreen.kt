package es.jvbabi.trails.page.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.page.home.components.CardSheetValue
import es.jvbabi.trails.page.home.components.DraggableCardSheet
import es.jvbabi.trails.page.home.components.DraggableCardSheetState
import es.jvbabi.trails.page.home.components.Map
import es.jvbabi.trails.page.home.components.rememberDraggableCardSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.settings
import trails.composeapp.generated.resources.shapes
import trails.composeapp.generated.resources.smartphone
import trails.composeapp.generated.resources.users

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val collapsedHeight = 72.dp
        val draggableCardSheetState = rememberDraggableCardSheetState(
            expandedHeight = maxHeight,
            semiExpandedHeight = 350.dp,
            collapsedHeight = collapsedHeight,
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
                            modifier = Modifier.fillMaxSize()
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
                val scope = rememberCoroutineScope()
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
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = animateDpAsState(if (draggableCardSheetState.isUserDragging) 5.dp else 4.dp).value + (contentPadding.calculateTopPadding() * draggableCardSheetState.expandedProgress))
                            .align(Alignment.TopCenter)
                            .width(animateDpAsState(if (draggableCardSheetState.isUserDragging) 52.dp else 40.dp).value)
                            .height(animateDpAsState(if (draggableCardSheetState.isUserDragging) 2.dp else 4.dp).value)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
//                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
//                        Row {
//                            Button(
//                                onClick = {
//                                    scope.launch { draggableCardSheetState.expand() }
//                                }
//                            ) { Text("expand") }
//                            Button(
//                                onClick = {
//                                    scope.launch { draggableCardSheetState.collapse() }
//                                }
//                            ) { Text("collaps") }
//                            Button(
//                                onClick = {
//                                    scope.launch { draggableCardSheetState.semiExpand() }
//                                }
//                            ) { Text("semi") }
//                        }
//                        Text(
//                            text = "Hier könnte eine Karte mit deinen Trails sein!",
//                            modifier = Modifier.padding(16.dp)
//                        )
//                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = contentPadding.calculateBottomPadding() * draggableCardSheetState.collapsedProgress)
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
                                containerColor = Color.Transparent,
                                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                NavigationBarItem(
                                    selected = state.selectedTab == HomeState.Tab.MyDevices,
                                    onClick = {
                                        onEvent(HomeEvent.SelectTab(HomeState.Tab.MyDevices))
                                        if (draggableCardSheetState.targetValue == CardSheetValue.Collapsed)
                                            scope.launch { draggableCardSheetState.semiExpand() }
                                        else if (draggableCardSheetState.targetValue == CardSheetValue.SemiExpanded)
                                            scope.launch { draggableCardSheetState.expand() }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(Res.drawable.smartphone),
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text("Meine Geräte") }
                                )

                                NavigationBarItem(
                                    selected = state.selectedTab == HomeState.Tab.Things,
                                    onClick = {
                                        onEvent(HomeEvent.SelectTab(HomeState.Tab.Things))
                                        if (draggableCardSheetState.targetValue == CardSheetValue.Collapsed)
                                            scope.launch { draggableCardSheetState.semiExpand() }
                                        else if (draggableCardSheetState.targetValue == CardSheetValue.SemiExpanded)
                                            scope.launch { draggableCardSheetState.expand() }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(Res.drawable.shapes),
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text("Gegenstände") }
                                )

                                NavigationBarItem(
                                    selected = state.selectedTab == HomeState.Tab.Shares,
                                    onClick = {
                                        onEvent(HomeEvent.SelectTab(HomeState.Tab.Shares))
                                        if (draggableCardSheetState.targetValue == CardSheetValue.Collapsed)
                                            scope.launch { draggableCardSheetState.semiExpand() }
                                        else if (draggableCardSheetState.targetValue == CardSheetValue.SemiExpanded)
                                            scope.launch { draggableCardSheetState.expand() }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(Res.drawable.users),
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text("Freigaben") }
                                )
                            }
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
