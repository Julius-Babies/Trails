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
import es.jvbabi.trails.page.home.HomeEvent
import es.jvbabi.trails.page.home.HomeScreen
import es.jvbabi.trails.page.home.HomeViewModel
import es.jvbabi.trails.page.home.MapEvent
import es.jvbabi.trails.page.home.MapViewModel
import es.jvbabi.trails.page.home.components.Map
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

    // These are held here, above the navigation display, so their state is already loaded when the
    // user reaches the destination that shows it. The map view model in particular keeps the map
    // surface alive: it is rendered below the navigation display and would otherwise be torn down
    // and rebuilt — renderer, style and camera — every time another area is opened on top of it.
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

                val mapState by mapViewModel.state.collectAsStateWithLifecycle()
                val hazeState = rememberHazeState()

                CompositionLocalProvider(LocalHazeState provides hazeState) {
                    // The map is the blur source the card sheet samples, so it carries hazeSource.
                    Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        Map(
                            state = mapState,
                            onDeviceClick = { device ->
                                homeViewModel.onEvent(HomeEvent.SelectDeviceOnMap(device.device.id))
                            },
                            onUserDragStart = { mapViewModel.onEvent(MapEvent.UserDragged) },
                        )
                    }

                    NavDisplay(
                        backStack = backstack,
                        onBack = { backstack.removeLastOrNull() },
                        entryProvider = { key ->
                            return@NavDisplay when (key) {
                                is Screen.Home -> NavEntry(key = key) {
                                    HomeScreen(
                                        viewModel = homeViewModel,
                                        mapViewModel = mapViewModel,
                                        backstack = backstack,
                                    )
                                }

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
                }

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

/** How far the surface behind the one being entered recedes, and vice versa on the way back. */
private const val AREA_DISTANT_SCALE = 1.35f
private const val AREA_NEAR_SCALE = 0.65f

private val areaFloatSpec = tween<Float>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing)
private val areaOffsetSpec = tween<IntOffset>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing)

/**
 * Holds the opacity up while the gesture is still early and only then accelerates it away, so
 * the surface being dismissed stays readable for most of the swipe.
 */
private val areaPredictiveFadeOutSpec =
    tween<Float>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutLinearInEasing)

/**
 * Settings is an area of its own rather than a peer of the home screen, so it enters and
 * leaves along the Z axis — the incoming surface grows in over the outgoing one, which
 * recedes. That reads as a change of level and keeps it distinguishable from the horizontal
 * push used for master/detail navigation inside the home tabs.
 */
private val settingsAreaTransitions: Map<String, Any> =
    NavDisplay.transitionSpec {
        scaleIn(areaFloatSpec, initialScale = AREA_NEAR_SCALE) + fadeIn(areaFloatSpec) togetherWith
                scaleOut(areaFloatSpec, targetScale = AREA_DISTANT_SCALE) + fadeOut(areaFloatSpec)
    } + NavDisplay.popTransitionSpec {
        scaleIn(areaFloatSpec, initialScale = AREA_DISTANT_SCALE) + fadeIn(areaFloatSpec) togetherWith
                scaleOut(areaFloatSpec, targetScale = AREA_NEAR_SCALE) + fadeOut(areaFloatSpec)
    } + NavDisplay.predictivePopTransitionSpec { swipeEdge ->
        // Same motion as a regular pop, but seeked by the gesture. The settings surface also
        // trails towards the edge being swiped so it follows the finger.
        val edgeDirection = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        scaleIn(areaFloatSpec, initialScale = AREA_DISTANT_SCALE) + fadeIn(areaFloatSpec) togetherWith
                scaleOut(areaFloatSpec, targetScale = AREA_NEAR_SCALE) +
                slideOutHorizontally(areaOffsetSpec) { width -> edgeDirection * width / 6 } +
                fadeOut(areaPredictiveFadeOutSpec)
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