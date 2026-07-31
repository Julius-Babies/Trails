package es.jvbabi.trails.page.home.components

import androidx.compose.runtime.Composable
import es.jvbabi.trails.page.home.HomeState
import es.jvbabi.trails.page.home.MapState

@Composable
expect fun Map(
    state: MapState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
    onUserDragStart: () -> Unit,
)
