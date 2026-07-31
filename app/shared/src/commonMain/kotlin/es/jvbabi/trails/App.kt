package es.jvbabi.trails

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.domain.repository.Key
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.Theme
import es.jvbabi.trails.domain.repository.UiRepository
import es.jvbabi.trails.page.Screen
import es.jvbabi.trails.page.home.HomeScreen
import es.jvbabi.trails.page.home.HomeViewModel
import es.jvbabi.trails.page.home.MapViewModel
import es.jvbabi.trails.page.setings.SettingsScreen
import es.jvbabi.trails.page.setings.SettingsViewModel
import es.jvbabi.trails.ui.components.LocalHazeState
import es.jvbabi.trails.ui.components.Snackbar
import es.jvbabi.trails.ui.overlay.device_deleted.DeviceDeletedOverlay
import es.jvbabi.trails.ui.overlay.update_available.UpdateAvailableOverlay
import es.jvbabi.trails.ui.theme.AppTheme
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

expect fun openUrl(url: String)
expect fun shareUrl(url: String, title: String?)
expect fun getClipboardText(): String?

@Composable
expect fun dynamicTheme(dark: Boolean): ColorScheme

val LocalAppTheme = staticCompositionLocalOf { Theme.System }

@Composable
@Preview
fun App(
    startNavigation: Screen? = null
) {

    val keyValueRepository = koinInject<KeyValueRepository>()

    // Held here so their state is already loaded when the user reaches the destination that shows
    // it, rather than the screen arriving empty and filling in mid transition.
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val mapViewModel = koinViewModel<MapViewModel>()

    val theme = keyValueRepository.get(Key.Theme)
        .map { it ?: Theme.System }
        .collectAsStateWithLifecycle(null)
        .value

    if (theme != null) {
        CompositionLocalProvider(LocalAppTheme provides theme) {
            AppTheme(
                dynamicColor = false,
                darkTheme = when(theme) {
                    Theme.Dark -> true
                    Theme.Light -> false
                    Theme.System -> isSystemInDarkTheme()
                }
            ) {
                val backstack = remember { mutableStateListOf<Screen>(Screen.Home) }

                LaunchedEffect(startNavigation) {
                    startNavigation?.let { backstack.add(it) }
                }

                DeviceDeletedOverlay()
                UpdateAvailableOverlay()

                val uiRepository = koinInject<UiRepository>()

                val currentSnackbar = uiRepository.currentSnackbar.collectAsStateWithLifecycle().value

                // Home is rendered outside the navigation display and stays composed for the
                // whole session, so the map keeps its renderer, style and camera no matter what
                // is opened on top of it. Its entry below therefore draws nothing — anything
                // full screen there would sit above the map and swallow its gestures.
                HomeScreen(
                    viewModel = homeViewModel,
                    mapViewModel = mapViewModel,
                    backstack = backstack,
                )

                NavDisplay(
                    backStack = backstack,
                    onBack = { backstack.removeLastOrNull() },
                    entryProvider = { key ->
                        return@NavDisplay when (key) {
                            is Screen.Home -> NavEntry(key = key) {}

                            is Screen.Settings -> NavEntry(
                                key = key,
                                metadata = settingsAreaTransitions,
                            ) {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
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
    }
}

private const val AREA_TRANSITION_DURATION_MILLIS = 250

/** How far the leaving surface drifts sideways under a predictive back gesture. */
private const val AREA_PREDICTIVE_EDGE_DRIFT = 8

private val areaOffsetSpec = tween<IntOffset>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing)
private val areaFadeInSpec = tween<Float>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing)

/**
 * Holds the opacity up while the gesture is still early and only then accelerates it away, so the
 * surface being dismissed stays readable for most of the travel.
 */
private val areaFadeOutSpec = tween<Float>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutLinearInEasing)

/**
 * Settings is an area of its own that covers the home screen, which stays composed and in place
 * below it. So rather than the two trading places, the settings surface travels the full height of
 * the display on its own — unmistakably a layer arriving over the map, and distinct from the
 * horizontal push used for master/detail navigation inside the home tabs.
 *
 * Home carries no transition of its own here: its entry draws nothing, the real one sits below the
 * navigation display and must not move.
 */
private val settingsAreaTransitions: Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInVertically(areaOffsetSpec) { height -> height } + fadeIn(areaFadeInSpec) togetherWith
                ExitTransition.None
    } + NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith
                slideOutVertically(areaOffsetSpec) { height -> height } + fadeOut(areaFadeOutSpec)
    } + NavDisplay.predictivePopTransitionSpec { swipeEdge ->
        // The same dismissal, but seeked by the gesture and drifting towards the swiped edge so it
        // follows the finger. Both offsets have to come from a single slide: combining two of them
        // keeps only the first.
        val edgeDirection = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        EnterTransition.None togetherWith
                slideOut(areaOffsetSpec) { size ->
                    IntOffset(
                        x = edgeDirection * size.width / AREA_PREDICTIVE_EDGE_DRIFT,
                        y = size.height,
                    )
                } + fadeOut(areaFadeOutSpec)
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