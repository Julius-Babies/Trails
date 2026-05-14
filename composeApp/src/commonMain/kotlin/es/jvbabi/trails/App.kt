package es.jvbabi.trails

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import es.jvbabi.trails.page.Screen
import es.jvbabi.trails.page.home.HomeScreen
import es.jvbabi.trails.page.setings.SettingsScreen

expect fun openUrl(url: String)

@Composable
@Preview
fun App(
    startNavigation: Screen? = null
) {
    MaterialTheme {
        val backstack = remember { mutableStateListOf<Screen>(Screen.Home) }

        LaunchedEffect(startNavigation) {
            startNavigation?.let { backstack.add(it) }
        }

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