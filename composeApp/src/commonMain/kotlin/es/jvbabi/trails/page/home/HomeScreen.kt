package es.jvbabi.trails.page.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.map.MaplibreMap
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.settings

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {}
) {
    Scaffold { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                FilledTonalIconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.settings),
                        contentDescription = "Settings"
                    )
                }
            }
        }
    }
}