@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.page.setings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.jvbabi.trails.domain.repository.Theme
import es.jvbabi.trails.ui.components.SteppedSlider
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.*
import kotlin.math.abs

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.minimumMovementMeters == null) return

    SettingsContent(
        state = state,
        onBack = onBack,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun SettingsContent(
    state: SettingsState,
    onBack: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left),
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "Oberfläche".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 4.dp)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = state.appTheme == Theme.System,
                    onClick = { onEvent(SettingsEvent.SetAppTheme(Theme.System)) },
                    icon = {
                        AnimatedContent(
                            targetState = state.appTheme == Theme.System,
                        ) { isSelected ->
                            Icon(
                                painter = painterResource(if (!isSelected) Res.drawable.sun_moon else Res.drawable.check),
                                contentDescription = "System",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    label = {
                        Text("Auto")
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                )
                SegmentedButton(
                    selected = state.appTheme == Theme.Light,
                    onClick = { onEvent(SettingsEvent.SetAppTheme(Theme.Light)) },
                    icon = {
                        AnimatedContent(
                            targetState = state.appTheme == Theme.Light,
                        ) { isSelected ->
                            Icon(
                                painter = painterResource(if (!isSelected) Res.drawable.sun else Res.drawable.check),
                                contentDescription = "Hell",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    label = {
                        Text("Hell")
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                )
                SegmentedButton(
                    selected = state.appTheme == Theme.Dark,
                    onClick = { onEvent(SettingsEvent.SetAppTheme(Theme.Dark)) },
                    icon = {
                        AnimatedContent(
                            targetState = state.appTheme == Theme.Dark,
                        ) { isSelected ->
                            Icon(
                                painter = painterResource(if (!isSelected) Res.drawable.moon else Res.drawable.check),
                                contentDescription = "Dunkel",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    label = {
                        Text("Dunkel")
                    },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                )
            }

            Text(
                text = "Tracking".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 4.dp)
            )

            val meterValues = SettingsState.DEFAULT_MINIMUM_MOVEMENT_METER_VALUES

            // Nearest default to the persisted value, so a value outside the list still maps to a step.
            val selectedIndex = remember(state.minimumMovementMeters) {
                val persisted = state.minimumMovementMeters ?: meterValues.first()
                meterValues.indices.minBy { abs(meterValues[it] - persisted) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.route),
                    contentDescription = null,
                )
                Column {
                    Text(
                        text = "Minimal erforderliche Bewegung",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Die minimale Distanz, die zurückgelegt werden muss, bevor ein neuer Punkt erstellt wird.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    SteppedSlider(
                        modifier = Modifier.padding(top = 8.dp),
                        stepCount = meterValues.size,
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { index ->
                            onEvent(SettingsEvent.UpdateMinimumMovementMeters(meterValues[index]))
                        },
                        thumbLabel = { index -> "${meterValues[index]}m" },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onEvent(SettingsEvent.RequestLocationPermissions) },
                enabled = state.hasLocationPermissions == false
            ) {
                Text("Standortberechtigungen anfordern")
            }

            Button(
                onClick = { onEvent(SettingsEvent.RequestNotificationPermissions) },
                enabled = state.hasNotificationPermissions == false
            ) {
                Text("Benachrichtigungen erlauben")
            }

            Button(
                onClick = { onEvent(SettingsEvent.RequestFullscreenIntentPermissions) },
                enabled = state.hasFullscreenIntentPermissions == false
            ) {
                Text("Vollbildaktivitäten erlauben")
            }

            Button(
                onClick = { onEvent(SettingsEvent.RequestUnrestrictedBatteryBackgroundUsage) },
                enabled = state.hasUnrestrictedBatteryBackgroundUsage == false
            ) {
                Text("Batterieoptimierungen aufheben")
            }

            Button(onClick = { onEvent(SettingsEvent.OpenLoginDialog) }) {
                Text("Anmelden")
            }

            Text("Trails Server: ${state.currentHomeserverUrl}")
            Text("Trails Device: ${state.thisDeviceId} ${state.thisDevice?.displayName}")
            Text("Trails User ID: ${state.userId}")
            Text("Nicht synchronisierte Snapshots: ${state.unsyncedSnapshotCount ?: "–"}")

            Button(
                onClick = { onEvent(SettingsEvent.RingDevice) }
            ) {
                Text("Device klingeln")
            }

            Button(
                onClick = {
                    if (state.isBackgroundTrackingServiceRunning) onEvent(SettingsEvent.StopTracking)
                    else onEvent(SettingsEvent.StartTracking)
                }
            ) {
                Text(if (state.isBackgroundTrackingServiceRunning) "Tracking stoppen" else "Tracking starten")
            }
        }
    }

    if (state.showLoginDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.CloseLoginDialog) },
            title = { Text("Anmelden") },
            text = {
                Column {
                    TextField(
                        value = state.homeServerUrl,
                        onValueChange = { onEvent(SettingsEvent.UpdateHomeServerUrl(it)) },
                        label = { Text("Home Server Domain") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onEvent(SettingsEvent.Login)
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
@Preview
private fun SettingsPreview() {
    SettingsContent(
        onBack = {},
        state = SettingsState(
            showLoginDialog = false,
            homeServerUrl = "https://trails.werkbank.space",
            hasLocationPermissions = true,

            appTheme = Theme.Light,
            minimumMovementMeters = 10,
        ),
        onEvent = {}
    )
}