@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package es.jvbabi.trails.page.devices.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import es.jvbabi.trails.page.devices.Screen
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.home.components.padding
import es.jvbabi.trails.ui.components.ConfigureTopBar
import es.jvbabi.trails.ui.components.LocalHazeState
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.arrow_left
import trails.app.shared.generated.resources.bell_ring
import trails.app.shared.generated.resources.smartphone
import trails.app.shared.generated.resources.smartphone_nfc
import trails.app.shared.generated.resources.trash_2
import kotlin.uuid.Uuid

@Composable
fun DeviceScreen(
    deviceId: Uuid,
    contentPadding: PaddingValues,
    backstack: MutableList<Screen>,
    nestedScrollConnection: NestedScrollConnection,
) {
    val viewModel = koinViewModel<DeviceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(deviceId) {
        viewModel.init(deviceId)
    }

    LaunchedEffect(state.deletionState) {
        if (state.deletionState is DeviceState.DeletionState.Success) {
            backstack.removeLastOrNull()
        }
    }

    DeviceContent(
        state = state,
        contentPadding = contentPadding,
        onEvent = viewModel::onEvent,
        nestedScrollConnection = nestedScrollConnection,
        onBack = { backstack.removeLastOrNull() },
    )
}

@Composable
fun DeviceContent(
    state: DeviceState,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    onEvent: (event: DeviceEvent) -> Unit,
    onBack: () -> Unit,
) {
    if (state.device == null) return

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val hazeStyle = HazeMaterials.thin()

    ConfigureTopBar(
        title = state.device.device.displayName,
        subtitle = state.device.device.owner.username,
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
                    .hazeEffect(LocalHazeState.current) {
                        blurEffect {
                            blurRadius = 8.dp
                            style = hazeStyle
                        }
                    }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_left),
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        actions = {
            if (state.currentUser != null && state.currentUser.id == state.device.device.owner.id) IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
                    .hazeEffect(LocalHazeState.current) {
                        blurEffect {
                            blurRadius = 8.dp
                            style = hazeStyle
                        }
                    }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.trash_2),
                    contentDescription = "Löschen",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    )

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        confirmButton = {
            TextButton(
                onClick = {
                    onEvent(DeviceEvent.Delete)
                },
                enabled = state.deletionState !is DeviceState.DeletionState.Loading
            ) {
                Text(
                    text = "Löschen",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showDeleteDialog = false },
                enabled = state.deletionState !is DeviceState.DeletionState.Loading
            ) {
                Text("Abbrechen")
            }
        },
        icon = {
            AnimatedContent(
                targetState = state.deletionState is DeviceState.DeletionState.Loading
            ) { isLoading ->
                if (isLoading) LoadingIndicator(Modifier.size(24.dp))
                else Icon(
                    painter = painterResource(Res.drawable.trash_2),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                    contentDescription = null,
                )
            }
        },
        title = {
            Text("Gerät löschen?")
        },
        text = {
            Column {
                Text("Dies kann nicht rückgängig gemacht werden. Alle Freigaben, die von dem Gerät erteilt wurden, verfallen.")
                var errorMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(state.deletionState) {
                    if (state.deletionState is DeviceState.DeletionState.Error) {
                        errorMessage = state.deletionState.message
                    }
                }

                AnimatedVisibility(
                    visible = state.deletionState is DeviceState.DeletionState.Error,
                    enter = expandVertically(expandFrom = Alignment.CenterVertically),
                    exit = shrinkVertically(shrinkTowards = Alignment.CenterVertically)
                ) {
                    Text(
                        text = "An error occurred: ${errorMessage.orEmpty()}",
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 6,
                    )
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(LocalHazeState.current)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding.copy(bottom = 0.dp))
                .padding(top = 64.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .aspectRatio(1f)
                            .clip(MaterialShapes.Cookie12Sided.toShape())
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = state.image != null,
                        ) { hasImage ->
                            val bitmap = rememberBitmapFromBytes(state.image)
                            if (!hasImage || bitmap == null) {
                                Icon(
                                    painter = painterResource(Res.drawable.smartphone),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp)
                                )
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    Column {
                        if (state.pingState != null) Button(
                            onClick = { onEvent(DeviceEvent.Ping) },
                            enabled = state.pingState == DeviceState.PingState.Ready
                        ) {
                            AnimatedContent(
                                targetState = state.pingState == DeviceState.PingState.Loading,
                            ) { isLoading ->
                                if (isLoading) LoadingIndicator(Modifier.size(16.dp))
                                else Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.bell_ring),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("Ping")
                                }
                            }
                        }

                        if (state.canRing) Button(
                            onClick = {}
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.smartphone_nfc),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Anklingeln")
                            }
                        }
                    }
                }
            }
        }
    }
}