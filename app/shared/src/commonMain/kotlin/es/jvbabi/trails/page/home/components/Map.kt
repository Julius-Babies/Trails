package es.jvbabi.trails.page.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.page.home.HomeState

@Composable
expect fun Map(
    state: HomeState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
    onCameraChanged: (HomeState.MapCamera) -> Unit,
    bottomPadding: Dp = 0.dp,
)
