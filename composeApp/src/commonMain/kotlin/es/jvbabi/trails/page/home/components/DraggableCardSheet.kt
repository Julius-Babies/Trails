package es.jvbabi.trails.page.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.page.home.components.internal.AnchoredDraggableState
import es.jvbabi.trails.page.home.components.internal.DraggableAnchors
import es.jvbabi.trails.page.home.components.internal.animateTo
import es.jvbabi.trails.page.home.components.internal.draggableAnchors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class CardSheetValue { Collapsed, SemiExpanded, Expanded }

@Stable
class DraggableCardSheetState(
    val expandedHeight: Dp,
    val semiExpandedHeight: Dp = Dp.Unspecified,
    val collapsedHeight: Dp,
    val density: Density,
    confirmValueChange: (CardSheetValue) -> Boolean = { true },
) {
    val hasSemiExpanded: Boolean
        get() = semiExpandedHeight != Dp.Unspecified &&
            semiExpandedHeight > collapsedHeight &&
            semiExpandedHeight < expandedHeight

    internal val anchoredDraggableState = AnchoredDraggableState(
        initialValue = CardSheetValue.Collapsed,
        positionalThreshold = { it * 0.5f },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
        animationSpec = { target ->
            if (target == CardSheetValue.Expanded) tween<Float>()
            else spring<Float>(dampingRatio = 0.7f, stiffness = 300f)
        },
        confirmValueChange = confirmValueChange,
    )

    private val _alpha = Animatable(1f)
    val alpha: Float get() = _alpha.value

    var isUserDragging: Boolean by mutableStateOf(false)
        private set

    val currentValue: CardSheetValue get() = anchoredDraggableState.currentValue
    val targetValue: CardSheetValue get() = anchoredDraggableState.targetValue
    internal val offsetPx: Float get() = anchoredDraggableState.offset

    /**
     * Progress of the animation between collapsed and expanded. 0f when collapsed, 1f when expanded.
     */
    val progress by derivedStateOf {
        val collapsedPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.Collapsed)
        val expandedPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.Expanded)
        val px = anchoredDraggableState.offset
        if (collapsedPos.isNaN() || expandedPos.isNaN() || px.isNaN() || collapsedPos == expandedPos) 0f
        else ((collapsedPos - px) / (collapsedPos - expandedPos)).coerceIn(0f, 1f)
    }

    /**
     * Progress of the animation between collapsed and semi-expanded. 0f when collapsed, 1f when semi-expanded. Always 1f if no semi-expanded state is defined.
     */
    val collapsedProgress by derivedStateOf {
        if (!hasSemiExpanded) return@derivedStateOf 1f
        val collapsedPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.Collapsed)
        val semiPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.SemiExpanded)
        val px = anchoredDraggableState.offset
        if (collapsedPos.isNaN() || semiPos.isNaN() || px.isNaN() || collapsedPos == semiPos) 0f
        else ((collapsedPos - px) / (collapsedPos - semiPos)).coerceIn(0f, 1f)
    }

    /**
     * Progress of the animation between semi-expanded and expanded. 0f when semi-expanded, 1f when expanded. Always 0f if no semi-expanded state is defined.
     */
    val expandedProgress by derivedStateOf {
        if (!hasSemiExpanded) return@derivedStateOf progress
        val semiPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.SemiExpanded)
        val expandedPos = anchoredDraggableState.anchors.positionOf(CardSheetValue.Expanded)
        val px = anchoredDraggableState.offset
        if (semiPos.isNaN() || expandedPos.isNaN() || px.isNaN() || semiPos == expandedPos) 0f
        else ((semiPos - px) / (semiPos - expandedPos)).coerceIn(0f, 1f)
    }

    suspend fun expand(velocity: Float = 0f) {
        _alpha.snapTo(0.7f)
        coroutineScope {
            launch { anchoredDraggableState.animateTo(CardSheetValue.Expanded, velocity) }
            launch { _alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        }
    }

    suspend fun semiExpand(velocity: Float = 0f) {
        if (!hasSemiExpanded) { expand(velocity); return }
        _alpha.snapTo(0.7f)
        coroutineScope {
            launch { anchoredDraggableState.animateTo(CardSheetValue.SemiExpanded, velocity) }
            launch { _alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        }
    }

    suspend fun collapse(velocity: Float = 0f) {
        _alpha.snapTo(0.7f)
        coroutineScope {
            launch { anchoredDraggableState.animateTo(CardSheetValue.Collapsed, velocity) }
            launch { _alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        }
    }

    internal suspend fun handleFling(velocityY: Float) {
        val currentPx = anchoredDraggableState.offset
        if (currentPx.isNaN()) return
        _alpha.snapTo(0.7f)
        coroutineScope {
            launch { anchoredDraggableState.settle(velocityY) }
            launch { _alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        }
    }

    internal fun setUserDragging(isDragging: Boolean) {
        isUserDragging = isDragging
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        private fun anchorPos(value: CardSheetValue) =
            anchoredDraggableState.anchors.positionOf(value)

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            val currentPx = anchoredDraggableState.offset
            val expandedPx = anchorPos(CardSheetValue.Expanded)
            if (delta < 0 && !currentPx.isNaN() && !expandedPx.isNaN() && currentPx > expandedPx) {
                val consumed = anchoredDraggableState.dispatchRawDelta(delta)
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val delta = available.y
            val currentPx = anchoredDraggableState.offset
            val collapsedPx = anchorPos(CardSheetValue.Collapsed)
            if (delta > 0 && !currentPx.isNaN() && !collapsedPx.isNaN() && currentPx < collapsedPx) {
                val consumedDelta = anchoredDraggableState.dispatchRawDelta(delta)
                return Offset(0f, consumedDelta)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            handleFling(available.y)
            return Velocity.Zero
        }
    }
}

@Composable
fun DraggableCardSheet(
    modifier: Modifier,
    state: DraggableCardSheetState,
    content: @Composable () -> Unit,
    cardContent: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val systemBar = WindowInsets.safeContent.asPaddingValues()
    val isAnimating = state.anchoredDraggableState.isAnimationRunning

    val horizontalContainerPadding by derivedStateOf {
        systemBar.calculateBottomPadding() * (1f - state.collapsedProgress) +
                8.dp * (1f - state.expandedProgress)
    }

    val collapsedContainerPadding = systemBar.calculateBottomPadding() + 8.dp
    val semiContainerPadding = 8.dp

    val bottomContainerPadding by derivedStateOf {
        if (!state.hasSemiExpanded) {
            collapsedContainerPadding * (1f - state.progress)
        } else if (state.anchoredDraggableState.offset >= state.anchoredDraggableState.anchors.positionOf(CardSheetValue.SemiExpanded)) {
            collapsedContainerPadding +
                    (semiContainerPadding - collapsedContainerPadding) * state.collapsedProgress
        } else {
            semiContainerPadding * (1f - state.expandedProgress)
        }
    }

    val contentPadding by derivedStateOf {
        PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp + systemBar.calculateTopPadding() * state.expandedProgress,
            bottom = 8.dp + systemBar.calculateBottomPadding() * (
                    if (state.hasSemiExpanded) state.collapsedProgress else state.progress
                    )
        )
    }

    val sheetHeight by derivedStateOf {
        val collapsedPos = state.anchoredDraggableState.anchors.positionOf(CardSheetValue.Collapsed)
        val semiPos = state.anchoredDraggableState.anchors.positionOf(CardSheetValue.SemiExpanded)
        val expandedPos = state.anchoredDraggableState.anchors.positionOf(CardSheetValue.Expanded)
        val px = state.offsetPx

        if (collapsedPos.isNaN() || expandedPos.isNaN() || px.isNaN()) {
            state.collapsedHeight
        } else if (!state.hasSemiExpanded || semiPos.isNaN()) {
            val progress = ((collapsedPos - px) / (collapsedPos - expandedPos)).coerceIn(0f, 1f)
            state.collapsedHeight + (state.expandedHeight - state.collapsedHeight) * progress
        } else if (px >= semiPos) {
            val progress = ((collapsedPos - px) / (collapsedPos - semiPos)).coerceIn(0f, 1f)
            state.collapsedHeight + (state.semiExpandedHeight - state.collapsedHeight) * progress
        } else {
            val progress = ((semiPos - px) / (semiPos - expandedPos)).coerceIn(0f, 1f)
            state.semiExpandedHeight + (state.expandedHeight - state.semiExpandedHeight) * progress
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        val anchors: (IntSize, Constraints) -> Pair<DraggableAnchors<CardSheetValue>, CardSheetValue> = { _, constraints ->
            val fullHeight = constraints.maxHeight.toFloat()
            val collapsedPx = with(state.density) { state.collapsedHeight.toPx() }
            val collapsedContainerPaddingPx = with(state.density) { collapsedContainerPadding.toPx() }
            val semiContainerPaddingPx = with(state.density) { semiContainerPadding.toPx() }
            val expandedPx = with(state.density) { state.expandedHeight.toPx() }
            val newAnchors = DraggableAnchors<CardSheetValue> {
                CardSheetValue.Collapsed at (fullHeight - collapsedPx - collapsedContainerPaddingPx)
                if (state.hasSemiExpanded) {
                    CardSheetValue.SemiExpanded at (
                        fullHeight -
                            with(state.density) { state.semiExpandedHeight.toPx() } -
                            semiContainerPaddingPx
                    )
                }
                CardSheetValue.Expanded at (fullHeight - expandedPx)
            }
            val newTarget = when (state.anchoredDraggableState.targetValue) {
                CardSheetValue.Expanded -> if (newAnchors.hasAnchorFor(CardSheetValue.Expanded)) CardSheetValue.Expanded else CardSheetValue.Collapsed
                CardSheetValue.SemiExpanded -> when {
                    newAnchors.hasAnchorFor(CardSheetValue.SemiExpanded) -> CardSheetValue.SemiExpanded
                    newAnchors.hasAnchorFor(CardSheetValue.Expanded) -> CardSheetValue.Expanded
                    else -> CardSheetValue.Collapsed
                }
                CardSheetValue.Collapsed -> CardSheetValue.Collapsed
            }
            newAnchors to newTarget
        }

        val shape = RoundedCornerShape(
            WindowInsets.safeContent.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)
        )

        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .draggableAnchors(
                        state = state.anchoredDraggableState,
                        orientation = Orientation.Vertical,
                        anchors = anchors,
                    )
                    .draggable(
                        state = state.anchoredDraggableState.draggableState,
                        orientation = Orientation.Vertical,
                        enabled = true,
                        startDragImmediately = isAnimating,
                        onDragStarted = { state.setUserDragging(true) },
                        onDragStopped = { velocity ->
                            state.setUserDragging(false)
                            scope.launch { state.handleFling(velocity) }
                        },
                    )
                    .nestedScroll(state.nestedScrollConnection)
                    .padding(horizontal = horizontalContainerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight + bottomContainerPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = state.alpha))
                    ) {
                        cardContent(contentPadding)
                    }
                    Spacer(modifier = Modifier.height(bottomContainerPadding))
                }
            }
        }
    }
}

@Composable
fun rememberDraggableCardSheetState(
    expandedHeight: Dp = 400.dp,
    semiExpandedHeight: Dp = Dp.Unspecified,
    collapsedHeight: Dp = 100.dp,
): DraggableCardSheetState {
    val density = LocalDensity.current
    return remember(density, expandedHeight, semiExpandedHeight, collapsedHeight) {
        DraggableCardSheetState(
            expandedHeight = expandedHeight,
            semiExpandedHeight = semiExpandedHeight,
            collapsedHeight = collapsedHeight,
            density = density,
        )
    }
}

@Preview
@Composable
fun DraggableCardSheetPreview() {
    DraggableCardSheet(
        modifier = Modifier.fillMaxSize(),
        state = rememberDraggableCardSheetState(),
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("Content")
            }
        },
        cardContent = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            ) {
                Text("Card content")
                Text("Card content")
                Text("Card content")
                Text("Card content")
            }
        }
    )
}
