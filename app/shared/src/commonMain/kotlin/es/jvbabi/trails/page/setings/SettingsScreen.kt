@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.page.setings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.jvbabi.trails.domain.repository.Theme
import es.jvbabi.trails.utils.padding
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.arrow_left
import trails.app.shared.generated.resources.check
import trails.app.shared.generated.resources.moon
import trails.app.shared.generated.resources.sun
import trails.app.shared.generated.resources.sun_moon

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
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
        ),
        onEvent = {}
    )
}