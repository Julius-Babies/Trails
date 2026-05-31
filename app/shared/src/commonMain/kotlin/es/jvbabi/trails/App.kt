package es.jvbabi.trails

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.domain.repository.UiRepository
import es.jvbabi.trails.page.Screen
import es.jvbabi.trails.page.home.HomeScreen
import es.jvbabi.trails.page.setings.SettingsScreen
import es.jvbabi.trails.ui.components.LocalHazeState
import es.jvbabi.trails.ui.components.Snackbar
import es.jvbabi.trails.ui.overlay.DeviceDeletedOverlay
import es.jvbabi.trails.ui.theme.AppTheme
import org.koin.compose.koinInject

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

        val uiRepository = koinInject<UiRepository>()

        val currentSnackbar = uiRepository.currentSnackbar.collectAsStateWithLifecycle().value

        NavDisplay(
            backStack = backstack,
            onBack = { backstack.removeLastOrNull() },
            entryProvider = { key ->
                return@NavDisplay when (key) {
                    is Screen.Home -> NavEntry(key = key) {
                        HomeScreen(
                            backstack = backstack,
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

        AnimatedContent(
            targetState = currentSnackbar,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp + WindowInsets.safeContent.asPaddingValues().calculateBottomPadding())
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            transitionSpec = {
                scaleIn() + slideInVertically { -it/2 } + fadeIn() togetherWith scaleOut() + slideOutVertically { -it/2 } + fadeOut()
            }
        ) { currentSnackbar ->
            if (currentSnackbar != null) {
                Snackbar(
                    modifier = Modifier.fillMaxWidth(),
                    snackbar = currentSnackbar
                )
            }
        }
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