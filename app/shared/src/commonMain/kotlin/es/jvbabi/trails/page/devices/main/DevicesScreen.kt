package es.jvbabi.trails.page.devices.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
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
            modifier = Modifier.fillMaxSize(),
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
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding.copy(bottom = 0.dp))
        ) {
            Text(
                text = "Geräte",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = contentPadding.bottom)
            ) {
                if (state.myDevices.isNotEmpty()) {
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
                }

                if (state.foreignDevices.isNotEmpty()) {
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

        if (state.isConnectedToHomeServer != null && state.isConnectedToHomeServer !is DevicesState.HomeServerConnectionState.NotLoggedIn) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .dropShadow(RoundedCornerShape(8.dp), shadow = Shadow(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (state.isConnectedToHomeServer is DevicesState.HomeServerConnectionState.Connected) Color.Green else Color.Red)
            )

            Text(
                text = if (state.isConnectedToHomeServer is DevicesState.HomeServerConnectionState.Connected) "Verbunden mit Trails Server" else "Nicht verbunden mit Trails Server",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
