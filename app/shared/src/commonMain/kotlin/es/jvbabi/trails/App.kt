package es.jvbabi.trails

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
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
import es.jvbabi.trails.utils.SyncHumanReadableLocale
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

    SyncHumanReadableLocale()

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

                // Progress of the area currently covering home, published by its AreaSurface.
                val areaProgress = remember { mutableStateOf<State<Float>?>(null) }

                // Home is rendered outside the navigation display and stays composed for the
                // whole session, so the map keeps its renderer, style and camera no matter what
                // is opened on top of it. Its entry below therefore draws nothing — anything
                // full screen there would sit above the map and swallow its gestures.
                //
                // While an area covers it, home recedes and rounds off against that area's
                // progress — inverted, so it is at its smallest and roundest exactly when the area
                // is fully covering. The layer is only in the chain for as long as an area is
                // present: the map is an interop view, and it should not sit behind a render layer
                // during normal use.
                Box(
                    modifier = if (areaProgress.value != null) {
                        Modifier.graphicsLayer {
                            val progress = areaProgress.value?.value ?: 1f
                            val scale = lerp(AREA_BACKGROUND_SCALE, 1f, progress)
                            scaleX = scale
                            scaleY = scale
                            clip = true
                            shape = RoundedCornerShape(AREA_CORNER_RADIUS * (1f - progress))
                        }
                    } else {
                        Modifier
                    }
                ) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        mapViewModel = mapViewModel,
                        backstack = backstack,
                    )
                }

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
                                AreaSurface(progressHolder = areaProgress) {
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onBack = remember { { backstack.removeLastOrNull() } }
                                    )
                                }
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

/**
 * Fractions of the surface's own size that it travels. Enough to read as leaving, not so much that
 * it slides off the display — it is dismissed by fading and rounding away as well.
 *
 * Pressing back and swiping it away use the same distance and the same easing on purpose. When a
 * gesture ends, Navigation3 stops using the predictive spec and settles the remaining fraction with
 * the pop spec, so the two have to describe the same motion or the surface jumps as the finger
 * lifts. Only the alpha floor and the sideways drift are particular to the gesture.
 */
private const val AREA_VERTICAL_TRAVEL = 4
private const val AREA_PREDICTIVE_EDGE_DRIFT = 12

/**
 * Opacity of an area surface at a given point in its travel.
 *
 * Squared rather than linear, so the surface keeps most of its opacity while the gesture is still in
 * the user's hand — a swipe that is 60% of the way is not 60% gone, it can still be cancelled — and
 * spends its opacity near the end instead.
 *
 * This cannot be expressed as a [fadeOut] on the transition spec. When a gesture ends, Navigation3
 * settles the remaining fraction while keeping the predictive spec in force, so a spec whose fade
 * stopped at some floor left the surface being cut away at that floor the moment it was removed from
 * composition. Driving opacity from the progress instead means one curve covers the gesture, the
 * settle and a plain back press alike, and it is genuinely at zero when the surface goes away.
 */
private fun areaAlphaAt(progress: Float): Float = 1f - progress * progress

/** How far the screen behind an area recedes while that area covers it. */
private const val AREA_BACKGROUND_SCALE = 0.92f

private val areaOffsetSpec = tween<IntOffset>(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing)

/**
 * Settings is an area of its own that covers the home screen, which stays composed and in place
 * below it. So rather than the two trading places, only the settings surface moves: it rises a
 * short way into view, and leaves by drifting back down while fading and rounding off its corners
 * (see [AreaSurface]). That reads as a layer over the map and stays distinct from the horizontal
 * push used for master/detail navigation inside the home tabs.
 *
 * Home carries no transition of its own here: its entry draws nothing, the real one sits below the
 * navigation display and must not move.
 */
/** Corner radius the surface of an area reaches once it has fully left. */
private val AREA_CORNER_RADIUS = 28.dp

/**
 * Wraps the surface of an area destination so its corners round off as it leaves and square up as
 * it settles.
 *
 * The radius is driven by the entry's own enter/exit transition rather than by the gesture
 * directly. Navigation3 seeks that transition with the predictive back gesture, so the corners
 * follow the swipe and unwind again when it is cancelled. The gesture's own touch position is not
 * reachable from here: the navigation event dispatcher hands a gesture to a single handler, and
 * that handler belongs to the [NavDisplay].
 */
@Composable
private fun AreaSurface(
    progressHolder: MutableState<State<Float>?>,
    content: @Composable () -> Unit,
) {
    val transition = LocalNavAnimatedContentScope.current.transition

    // 0 while the area is fully covering, 1 once it has left. Kept as a State and never read
    // during composition, so a frame of this animation costs a redraw rather than a recomposition
    // of the whole area.
    val progress = transition.animateFloat(
        transitionSpec = { tween(AREA_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing) },
        label = "areaProgress",
    ) { state ->
        if (state == EnterExitState.Visible) 0f else 1f
    }

    // Published so the screen behind can move against the same seeked progress.
    DisposableEffect(progress) {
        progressHolder.value = progress
        onDispose { progressHolder.value = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = areaAlphaAt(progress.value)
                clip = true
                shape = RoundedCornerShape(AREA_CORNER_RADIUS * progress.value)
            }
    ) {
        content()
    }
}

/** Opacity and corner radius are not here — [AreaSurface] drives both from the same progress. */
private val settingsAreaTransitions: Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInVertically(areaOffsetSpec) { height ->
            height / AREA_VERTICAL_TRAVEL
        } togetherWith ExitTransition.None
    } + NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith
                slideOutVertically(areaOffsetSpec) { height -> height / AREA_VERTICAL_TRAVEL }
    } + NavDisplay.predictivePopTransitionSpec { swipeEdge ->
        // The same dismissal, seeked by the gesture, drifting towards the swiped edge as well.
        // Both offsets have to come from a single slide: combining two of them keeps only the first.
        val edgeDirection = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        EnterTransition.None togetherWith
                slideOut(areaOffsetSpec) { size ->
                    IntOffset(
                        x = edgeDirection * size.width / AREA_PREDICTIVE_EDGE_DRIFT,
                        y = size.height / AREA_VERTICAL_TRAVEL,
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