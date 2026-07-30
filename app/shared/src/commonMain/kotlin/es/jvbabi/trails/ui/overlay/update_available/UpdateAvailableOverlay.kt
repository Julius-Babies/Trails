@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.ui.overlay.update_available

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.jvbabi.trails.ThemeWrapper
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.app_icon
import trails.app.shared.generated.resources.move_right

@Composable
fun UpdateAvailableOverlay() {
    val viewModel = koinViewModel<UpdateAvailableViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state == null) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it == SheetValue.Expanded || state!!.isDismissed }
    )

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.onEvent(UpdateAvailableEvent.RequestDismiss) },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            UpdateAvailableOverlayContent(
                onEvent = viewModel::onEvent,
                state = state!!,
            )
        }
    }

    LaunchedEffect(state?.isDismissed) {
        if (state?.isDismissed == true) {
            sheetState.hide()
        }
    }
}

@Composable
fun UpdateAvailableOverlayContent(
    onEvent: (event: UpdateAvailableEvent) -> Unit,
    state: UpdateAvailableState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(Modifier.weight(.5f))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = "Trails",
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .size(108.dp),
            )
        }
        Spacer(Modifier.weight(.4f))
        Text(
            text = "Update verfügbar",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ein Update für Trails ist verfügbar. Bitte installiere das Update, um die neuesten Funktionen zu erhalten.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.currentVersion,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Right,
            )

            Icon(
                painter = painterResource(Res.drawable.move_right),
                contentDescription = "Arrow",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outline,
            )

            AnimatedContent(
                targetState = state.latestVersion,
                modifier = Modifier.weight(1f),
            ) { latestVersion ->
                if (latestVersion != null) {
                    Text(
                        text = latestVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Left,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }
        Spacer(Modifier.weight(1.25f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onEvent(UpdateAvailableEvent.RequestDismiss) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Jetzt nicht"
                )
            }
            Button(
                onClick = { onEvent(UpdateAvailableEvent.Install) },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Installieren")
            }
        }
    }
}

@Preview(showBackground = true)
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun UpdateAvailableOverlayPreview() {
    UpdateAvailableOverlayContent(
        onEvent = {},
        state = UpdateAvailableState(
            isDismissed = false,
            currentVersion = "1.0.0",
            latestVersion = "1.1.0"
        )
    )
}