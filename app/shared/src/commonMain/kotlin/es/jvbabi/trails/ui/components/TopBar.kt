package es.jvbabi.trails.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import es.jvbabi.trails.ThemeWrapper
import org.jetbrains.compose.resources.painterResource
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.arrow_left

@Composable
fun TopBar(
    state: TopBarState,
    modifier: Modifier = Modifier,
) {
    val localDensity = LocalDensity.current
    var actionsWidth by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
    ) {
        AnimatedContent(
            targetState = state.config.navigationIcon,
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            modifier = Modifier.align(Alignment.CenterStart)
        ) { content ->
            content?.invoke()
        }

        val titleStartPadding by animateDpAsState(
            targetValue = if (state.config.navigationIcon != null) 48.dp else 8.dp,
            label = "Title start padding",
            animationSpec = spring()
        )

        val titleEndPadding by animateDpAsState(
            targetValue = actionsWidth.coerceAtLeast(8.dp),
            label = "Title end padding",
            animationSpec = spring()
        )

        AnimatedVisibility(
            visible = state.config.title.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 4.dp)
        ) {
            val hazeStyle = HazeMaterials.thin()
            Column(
                modifier = Modifier
                    .padding(start = titleStartPadding, end = titleEndPadding)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
                    .hazeEffect(LocalHazeState.current) {
                        blurEffect {
                            blurRadius = 8.dp
                            style = hazeStyle
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = state.config.title,
                    transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                AnimatedContent(
                    targetState = state.config.subtitle,
                    transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
                ) { subtitle ->
                    if (subtitle != null) Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.CenterEnd).animateContentSize(spring())) {
            Row(
                modifier = Modifier.onSizeChanged { size ->
                    with(localDensity) {
                        actionsWidth = size.width.toDp()
                    }
                }
            ) {
                state.config.actions(this)
            }
        }
    }
}

data class TopBarConfig(
    val title: String = "",
    val subtitle: String? = null,
    val navigationIcon: (@Composable () -> Unit)? = null,
    val actions: @Composable RowScope.() -> Unit = {},
)

@Stable
class TopBarState {
    private data class Entry(val key: Any, val config: TopBarConfig)

    private val stack = mutableStateListOf<Entry>()

    val config: TopBarConfig
        get() = stack.lastOrNull()?.config ?: TopBarConfig()

    internal fun push(key: Any, config: TopBarConfig) {
        val i = stack.indexOfFirst { it.key == key }
        if (i >= 0) stack[i] = Entry(key, config) else stack.add(Entry(key, config))
    }

    internal fun pop(key: Any) {
        stack.removeAll { it.key == key }
    }
}

val LocalTopBar = staticCompositionLocalOf<TopBarState> {
    error("LocalTopBar not provided — wrap your app with CompositionLocalProvider")
}

val LocalHazeState = staticCompositionLocalOf<HazeState> {
    error("HazeState not supplied")
}

@Composable
fun ConfigureTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topBar = LocalTopBar.current
    val key = remember { Any() }

    // Update on every recomposition (e.g. state-driven title changes)
    SideEffect {
        topBar.push(key, TopBarConfig(title, subtitle, navigationIcon, actions))
    }
    // Clean up when screen leaves composition
    DisposableEffect(Unit) {
        onDispose { topBar.pop(key) }
    }
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun TopBarTitleOnlyPreview() {
    TopBar(
        state = remember {
            TopBarState().apply {
                push("key", TopBarConfig(title = "Devices"))
            }
        }
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun TopBarWithSubtitlePreview() {
    TopBar(
        state = remember {
            TopBarState().apply {
                push("key", TopBarConfig(title = "Devices", subtitle = "3 connected"))
            }
        }
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun TopBarWithBackButtonPreview() {
    TopBar(
        state = remember {
            TopBarState().apply {
                push(
                    "key", TopBarConfig(
                        title = "Device A1B2",
                        subtitle = "Online",
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(painter = painterResource(Res.drawable.arrow_left), contentDescription = "Back")
                            }
                        },
                    )
                )
            }
        }
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun TopBarLongTextPreview() {
    TopBar(
        state = remember {
            TopBarState().apply {
                push(
                    "key", TopBarConfig(
                        title = "This Is A Very Long Title That Should Ellipsize",
                        subtitle = "And this subtitle is also unreasonably long for any normal screen",
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(painter = painterResource(Res.drawable.arrow_left), contentDescription = "Back")
                            }
                        },
                    )
                )
            }
        }
    )
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun TopBarEmptyPreview() {
    TopBar(state = remember { TopBarState() })
}