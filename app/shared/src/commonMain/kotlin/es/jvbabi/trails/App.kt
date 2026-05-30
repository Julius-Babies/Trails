package es.jvbabi.trails

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.page.Screen
import es.jvbabi.trails.page.home.HomeScreen
import es.jvbabi.trails.page.setings.SettingsScreen
import es.jvbabi.trails.ui.components.LocalHazeState
import es.jvbabi.trails.ui.overlay.DeviceDeletedOverlay
import es.jvbabi.trails.ui.theme.AppTheme

expect fun openUrl(url: String)
expect fun shareUrl(url: String, title: String?)
expect fun getClipboardText(): String?

@Composable
expect fun dynamicTheme(dark: Boolean): ColorScheme

@Composable
@Preview
fun App(
    startNavigation: Screen? = null
) {

    AppTheme(dynamicColor = false) {
        val backstack = remember { mutableStateListOf<Screen>(Screen.Home) }

        LaunchedEffect(startNavigation) {
            startNavigation?.let { backstack.add(it) }
        }

        DeviceDeletedOverlay()

        NavDisplay(
            backStack = backstack,
            onBack = { backstack.removeLastOrNull() },
            entryProvider = { key ->
                return@NavDisplay when (key) {
                    is Screen.Home -> NavEntry(key = key) {
                        HomeScreen(
                            onOpenSettings = remember { { backstack.add(Screen.Settings) } }
                        )
                    }

                    is Screen.Settings -> NavEntry(key = key) {
                        SettingsScreen(
                            onBack = remember { { backstack.removeLastOrNull() } }
                        )
                    }
                }
            }
        )
    }
}

class ThemeWrapper: PreviewWrapperProvider {

    @Composable
    override fun Wrap(content: @Composable (() -> Unit)) {
        AppTheme(dynamicColor = false) {
            val hazeState = rememberHazeState()
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                Box(Modifier.hazeSource(hazeState)) {
                    content()
                }
            }
        }
    }
}