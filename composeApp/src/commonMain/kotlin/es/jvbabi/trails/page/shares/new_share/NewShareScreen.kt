package es.jvbabi.trails.page.shares.new_share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.page.home.bottomFadeOut
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.home.components.padding
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import es.jvbabi.trails.utils.toDp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.battery_medium
import trails.composeapp.generated.resources.link
import trails.composeapp.generated.resources.map_pin_time
import trails.composeapp.generated.resources.tag
import trails.composeapp.generated.resources.users
import trails.composeapp.generated.resources.x
import kotlin.uuid.Uuid

@Composable
fun NewShareScreen(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    close: () -> Unit,
) {

    val viewModel = koinViewModel<NewShareViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    NewShareContent(
        state = state,
        contentPadding = contentPadding,
        onEvent = viewModel::onEvent,
        nestedScrollConnection = nestedScrollConnection,
        close = close,
    )
}

@Composable
fun NewShareContent(
    contentPadding: PaddingValues,
    state: NewShareState,
    nestedScrollConnection: NestedScrollConnection?,
    onEvent: (event: NewShareEvent) -> Unit,
    close: () -> Unit,
) {
    if (state.currentDevice == null) return

    val localHapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .padding(contentPadding.copy(bottom = 0.dp))
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val titleFont = MaterialTheme.typography.headlineMedium
            Text(
                text = "Neue Freigabe erstellen",
                style = titleFont,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .height(titleFont.lineHeight.toDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = close,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.x),
                        contentDescription = "Schließen"
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, true)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomFadeOut()
                    .let { if (nestedScrollConnection != null) it.nestedScroll(nestedScrollConnection) else it }
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                    ) {
                        AnimatedContent(
                            targetState = state.image != null,
                        ) { hasImage ->
                            val bitmap = rememberBitmapFromBytes(state.image)
                            if (!hasImage) Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) else {
                                Image(
                                    bitmap = bitmap!!,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                )
                            }
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.currentDevice.displayName + " von " + state.currentDevice.owner.username,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = state.currentDevice.friendlyName + " (" + state.currentDevice.model + ")",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        (state.currentDevice.batteryState as? Device.BatteryState.Shared)?.let {
                            Text(
                                text = "${it.percentage}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Text(
                    text = "Du wirst den Standort für dieses Gerät teilen.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.map_pin_time),
                        contentDescription = null,
                    )
                    Column {
                        Text(
                            text = "Standortfreigabe",
                            style = MaterialTheme.typography.titleMedium
                        )
                        AnimatedContent(
                            modifier = Modifier.fillMaxWidth(),
                            targetState = state.selectedLocationShareHistoryState,
                            transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { -it } }
                        ) { duration ->
                            Text(
                                text = when (duration) {
                                    NewShareState.LocationShareHistoryState.NoHistory -> "Nur aktuellen Standort"
                                    NewShareState.LocationShareHistoryState.OneHour -> "Standortverlauf der letzten Stunde"
                                    NewShareState.LocationShareHistoryState.SixHours -> "Standortverlauf der letzten 6 Stunden"
                                    NewShareState.LocationShareHistoryState.OneDay -> "Standortverlauf des letzten Tages"
                                    NewShareState.LocationShareHistoryState.OneWeek -> "Standortverlauf der letzten Woche"
                                    NewShareState.LocationShareHistoryState.Infinite -> "Vollständiger Standortverlauf"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Slider(
                            modifier = Modifier.padding(top = 8.dp),
                            value = state.selectedLocationShareHistoryState.ordinal.toFloat(),
                            onValueChange = {
                                onEvent(NewShareEvent.LocationShareHistoryStateChanged(NewShareState.LocationShareHistoryState.entries[it.toInt()]))
                                localHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            valueRange = 0f..(NewShareState.LocationShareHistoryState.entries.size - 1).toFloat(),
                            steps = NewShareState.LocationShareHistoryState.entries.size - 2,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onEvent(NewShareEvent.ShareBatteryLevelChanged) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.battery_medium),
                        contentDescription = null,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Batteriestand teilen",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Checkbox(
                        checked = state.shareBatteryLevel,
                        onCheckedChange = { onEvent(NewShareEvent.ShareBatteryLevelChanged) },
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.tag),
                        contentDescription = null,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Freigabe benennen",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            value = state.shareName,
                            onValueChange = { onEvent(NewShareEvent.ShareNameChanged(it)) },
                            placeholder = { Text("z.B. 'Freigabe für Familie'") },
                            singleLine = true,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onEvent(NewShareEvent.AllowMultiuseLinkChanged) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.users),
                        contentDescription = null,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Mehrfachnutzung zulassen",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Checkbox(
                        checked = state.allowMultiuseLink,
                        onCheckedChange = { onEvent(NewShareEvent.AllowMultiuseLinkChanged) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.link),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("Link teilen")
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun NewShareScreenPreview() {
    NewShareContent(
        contentPadding = PaddingValues(),
        nestedScrollConnection = null,
        state = NewShareState(
            image = null,
            currentDevice = Device(
                id = Uuid.random(),
                manufacturer = "Google",
                model = "panther",
                friendlyName = "Pixel 7",
                displayName = "Google Pixel 7",
                batteryState = Device.BatteryState.Shared(
                    percentage = 49,
                    isCharging = false,
                ),
                owner = User(
                    id = Uuid.random(),
                    homeserver = "trails.werkbank.dev",
                    username = "testuser",
                )
            ),
        ),
        onEvent = {},
        close = {},
    )
}