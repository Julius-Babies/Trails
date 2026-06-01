@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package es.jvbabi.trails.page.devices.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import es.jvbabi.trails.ui.components.ConfigureTopBar
import es.jvbabi.trails.ui.components.DeviceImage
import es.jvbabi.trails.ui.components.LocalHazeState
import es.jvbabi.trails.utils.PaddingValues
import es.jvbabi.trails.utils.padding
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.*
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
        navigationIcon = remember { {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .hazeEffect(LocalHazeState.current) {
                        blurEffect {
                            blurRadius = 8.dp
                            style = hazeStyle
                        }
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_left),
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                )
            }
        } },
        actions = {
            if (state.currentUser != null && state.currentUser.id == state.device.device.owner.id) IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .hazeEffect(LocalHazeState.current) {
                        blurEffect {
                            blurRadius = 8.dp
                            style = hazeStyle
                        }
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
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
                    val bitmap = rememberBitmapFromBytes(state.image)
                    DeviceImage(
                        bitmap = bitmap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .aspectRatio(1f),
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                        imageFillFraction = .55f,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
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

                        if (state.ringState != null && state.ringState != DeviceState.RingState.Disabled) {
                            if (state.ringState == DeviceState.RingState.Ringing) {
                                Button(
                                    onClick = { onEvent(DeviceEvent.StopRing) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
                                        Text("Klingeln beenden")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onEvent(DeviceEvent.Ring) }
                                ) {
                                    AnimatedContent(
                                        targetState = state.ringState == DeviceState.RingState.Loading,
                                    ) { isLoading ->
                                        if (isLoading) LoadingIndicator(Modifier.size(16.dp))
                                        else Row(
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
        }
    }
}