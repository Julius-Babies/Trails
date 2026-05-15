package es.jvbabi.trails.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.page.home.components.DraggableCardSheet
import es.jvbabi.trails.page.home.components.Map
import es.jvbabi.trails.page.home.components.rememberDraggableCardSheetState
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
    val hazeState = rememberHazeState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val draggableCardSheetState = rememberDraggableCardSheetState(
            expandedHeight = maxHeight,
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
            cardContent = {
                val scope = rememberCoroutineScope()
                val hazeStyle = HazeMaterials.thin()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .hazeEffect(hazeState) {
                            blurEffect {
                                blurRadius = 4.dp + draggableCardSheetState.progress * 4.dp
                                style = hazeStyle
                            }
                        }
                ) {
                    Row {
                        Button(
                            onClick = {
                                scope.launch { draggableCardSheetState.expand() }
                            }
                        ) { Text("expand") }
                        Button(
                            onClick = {
                                scope.launch { draggableCardSheetState.collapse() }
                            }
                        ) { Text("collaps") }
                    }
                    Text(
                        text = "Hier könnte eine Karte mit deinen Trails sein!",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        )
    }

}
