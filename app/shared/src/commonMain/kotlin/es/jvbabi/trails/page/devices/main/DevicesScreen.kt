package es.jvbabi.trails.page.devices.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import es.jvbabi.trails.page.devices.Screen
import es.jvbabi.trails.page.devices.main.components.DeviceCard
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.home.components.padding
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevicesTab(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
) {
    val backstack = remember { mutableStateListOf<Screen>(Screen.Main) }

    key(contentPadding) {
        NavDisplay(
            backStack = backstack,
            onBack = { backstack.removeLastOrNull() },
            entryProvider = { key ->
                return@NavDisplay when (key) {
                    is Screen.Main -> NavEntry(key = key) {
                        DevicesScreen(
                            contentPadding = contentPadding,
                            nestedScrollConnection = nestedScrollConnection,
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun DevicesScreen(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
) {

    val viewModel = koinViewModel<DevicesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    DevicesContent(
        contentPadding = contentPadding,
        nestedScrollConnection = nestedScrollConnection,
        state = state,
    )
}

@Composable
fun DevicesContent(
    contentPadding: PaddingValues,
    state: DevicesState,
    nestedScrollConnection: NestedScrollConnection,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.copy(bottom = 0.dp))
    ) {
        Text(
            text = "Geräte",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.bottom)
        ) {
            Text(
                text = "Meine Geräte",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            state.myDevices.forEach { myDevice ->
                DeviceCard(
                    modifier = Modifier.fillMaxWidth(),
                    device = myDevice,
                    onClick = {}
                )
            }

            Text(
                text = "Mit mir geteilte Geräte",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            state.foreignDevices.forEach { foreignDevice ->
                DeviceCard(
                    modifier = Modifier.fillMaxWidth(),
                    device = foreignDevice,
                    onClick = {}
                )
            }
        }
    }
}
