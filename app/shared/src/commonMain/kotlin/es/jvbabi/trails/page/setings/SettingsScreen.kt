@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.page.setings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.arrow_left

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
            TopAppBar(
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
            Button(onClick = { onEvent(SettingsEvent.OpenLoginDialog) }) {
                Text("Anmelden")
            }

            Button(
                onClick = { onEvent(SettingsEvent.RequestLocationPermissions) },
                enabled = state.hasLocationPermissions == false
            ) {
                Text("Standortberechtigungen anfordern")
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
        ),
        onEvent = {}
    )
}