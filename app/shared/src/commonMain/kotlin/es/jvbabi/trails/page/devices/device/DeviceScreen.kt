@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package es.jvbabi.trails.page.devices.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import es.jvbabi.trails.page.devices.Screen
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.home.components.padding
import es.jvbabi.trails.ui.components.ConfigureTopBar
import es.jvbabi.trails.ui.components.LocalHazeState
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.arrow_left
import trails.app.shared.generated.resources.trash_2
import kotlin.uuid.Uuid

@Composable
fun DeviceScreen(
    deviceId: Uuid,
    contentPadding: PaddingValues,
    backstack: MutableList<Screen>,
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
        onBack = { backstack.removeLastOrNull() },
    )
}

@Composable
fun DeviceContent(
    state: DeviceState,
    contentPadding: PaddingValues,
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
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding.copy(bottom = 0.dp))
        ) {

        }
    }
}