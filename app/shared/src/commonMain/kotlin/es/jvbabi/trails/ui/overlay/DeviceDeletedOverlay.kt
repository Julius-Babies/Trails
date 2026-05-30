@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package es.jvbabi.trails.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.move_right
import trails.app.shared.generated.resources.smartphone
import trails.app.shared.generated.resources.trash_2
import kotlin.uuid.Uuid

@Composable
fun DeviceDeletedOverlay() {

    val viewModel = koinViewModel<DeviceDeletedViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it == SheetValue.Expanded || state?.isDismissed == true }
    )

    LaunchedEffect(state?.isDismissed) {
        if (state?.isDismissed == true) sheetState.hide()
    }

    if (state != null) ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.onEvent(DeviceDeletedEvent.Dismissed) },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            DeviceDeletedContent(
                onEvent = viewModel::onEvent,
                state = state!!,
            )
        }
    }
}

@Composable
fun DeviceDeletedContent(
    onEvent: (event: DeviceDeletedEvent) -> Unit,
    state: DeviceDeletedState,
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
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.image != null,
                ) { hasImage ->
                    val bitmap = rememberBitmapFromBytes(state.image)
                    if (!hasImage) {
                        Icon(
                            painter = painterResource(Res.drawable.smartphone),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        )
                    }
                }
            }

            Icon(
                painter = painterResource(Res.drawable.move_right),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.outline,
            )

            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.trash_2),
                    contentDescription = "Trash",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(.4f))
        Text(
            text = "Gerät entfernt",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Dieses Gerät wurde von ${state.deletedByDevice} aus deinem Trails-Account entfernt. Es wird keine Standortdaten mehr an deinen Homeserver senden.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.25f))
        Button(
            onClick = { onEvent(DeviceDeletedEvent.RequestDismiss) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "OK")
        }
    }
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun DeviceDeletedPreview() {
    DeviceDeletedContent(
        onEvent = {},
        state = DeviceDeletedState(
            device = Device(
                id = Uuid.random(),
                manufacturer = "Google",
                model = "panther",
                friendlyName = "Pixel 7",
                displayName = "Google Pixel 7",
                owner = User(
                    id = Uuid.random(),
                    homeserver = "trailsdevelopment.jvbabi.es",
                    username = "test"
                ),
                batteryState = Device.BatteryState.NotShared,
            ),
            deletedByDevice = "iPhone 12",
            image = null,
        )
    )
}