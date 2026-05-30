package es.jvbabi.trails.page.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.page.home.components.internal.AnchoredDraggableState
import es.jvbabi.trails.utils.getBottomBorderRadius
import es.jvbabi.trails.page.home.components.internal.DraggableAnchors
import es.jvbabi.trails.page.home.components.internal.animateTo
import es.jvbabi.trails.page.home.components.internal.draggableAnchors
import es.jvbabi.trails.utils.IntPaddingValues
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class CardSheetValue { Collapsed, SemiExpanded, Expanded }

@Composable
internal expect fun SheetBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: (progress: Float) -> Unit,
)

data class PaddingValues(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
) {
    constructor(all: Dp) : this(all, all, all, all)
    constructor(): this(0.dp)

    @Composable
    fun toIntPaddingValues(localDensity: Density): IntPaddingValues {
        with(localDensity) {
            return IntPaddingValues(
                top = this@PaddingValues.top.roundToPx(),
                bottom = this@PaddingValues.bottom.roundToPx(),
                start = this@PaddingValues.start.roundToPx(),
                end = this@PaddingValues.end.roundToPx(),
            )
        }
    }
}

@Composable
operator fun androidx.compose.foundation.layout.PaddingValues.plus(other: PaddingValues): PaddingValues =
    PaddingValues(
        top = this.calculateTopPadding() + other.top,
        bottom = this.calculateBottomPadding() + other.bottom,
        start = this.calculateStartPadding(LocalLayoutDirection.current) + other.start,
        end = this.calculateEndPadding(LocalLayoutDirection.current) + other.end
    )

fun Modifier.padding(paddingValues: PaddingValues): Modifier = this.then(
    Modifier.padding(
        start = paddingValues.start,
        top = paddingValues.top,
        end = paddingValues.end,
        bottom = paddingValues.bottom,
    )
)

@Stable
class DraggableCardSheetState(
    val expandedHeight: Dp,
    val semiExpandedHeight: Dp = Dp.Unspecified,
    val collapsedHeight: Dp,
    val density: Density,
    val initialValue: CardSheetValue,
    confirmValueChange: (CardSheetValue) -> Boolean = { true },
) {

    val predictiveBackGesturePreviewCoefficient = .75f

    val hasSemiExpanded: Boolean
        get() = semiExpandedHeight != Dp.Unspecified &&
                semiExpandedHeight > collapsedHeight &&
                semiExpandedHeight < expandedHeight

    internal val anchoredDraggableState = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = { it * 0.5f },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
        animationSpec = { target ->
            if (target == CardSheetValue.Expanded) tween()
            else spring(dampingRatio = 0.7f, stiffness = 300f)
        },
        confirmValueChange = confirmValueChange,
    )

    private val _alpha = Animatable(1f)
    val alpha: Float get() = _alpha.value

    var isUserDragging: Boolean by mutableStateOf(false)
        private set

    var backPreviewProgress: Float by mutableStateOf(0f)
        private set

    private var backPreviewBaseOffsetPx: Float = Float.NaN
    private var backPreviewAppliedOffsetPx: Float = 0f
    private var backPreviewStartValue: CardSheetValue? = null
    private var backPreviewTargetValue: CardSheetValue? = null

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
        val collapsedPx = anchoredDraggableState.anchors.positionOf(CardSheetValue.Collapsed)
        if (!collapsedPx.isNaN() && velocityY > 0f && currentPx >= collapsedPx) {
            collapse(velocityY)
            return
        }
        _alpha.snapTo(0.7f)
        coroutineScope {
            launch { anchoredDraggableState.settle(velocityY) }
            launch { _alpha.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        }
    }

    internal fun setUserDragging(isDragging: Boolean) {
        isUserDragging = isDragging
    }

    internal fun setBackPreviewProgress(progress: Float) {
        backPreviewProgress = progress.coerceIn(0f, 1f)
    }

    private fun backPreviewTarget(from: CardSheetValue): CardSheetValue = when (from) {
        CardSheetValue.Expanded ->
            if (hasSemiExpanded) CardSheetValue.SemiExpanded else CardSheetValue.Collapsed
        CardSheetValue.SemiExpanded -> CardSheetValue.Collapsed
        CardSheetValue.Collapsed -> CardSheetValue.Collapsed
    }

    internal fun applyBackPreview(progress: Float) {
        if (backPreviewStartValue == null) {
            backPreviewStartValue = currentValue
            backPreviewTargetValue = backPreviewTarget(currentValue)
            backPreviewBaseOffsetPx = anchoredDraggableState.offset
            backPreviewAppliedOffsetPx = 0f
        }
        val targetValue = backPreviewTargetValue ?: backPreviewTarget(currentValue)
        val currentPos = anchoredDraggableState.offset
        val targetPos = anchoredDraggableState.anchors.positionOf(targetValue)
        if (currentPos.isNaN() || targetPos.isNaN()) {
            setBackPreviewProgress(0f)
            backPreviewBaseOffsetPx = Float.NaN
            backPreviewAppliedOffsetPx = 0f
            backPreviewStartValue = null
            backPreviewTargetValue = null
            return
        }

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped == 0f) {
            if (backPreviewAppliedOffsetPx != 0f) {
                anchoredDraggableState.previewToOffset(backPreviewBaseOffsetPx)
            }
            backPreviewBaseOffsetPx = Float.NaN
            backPreviewAppliedOffsetPx = 0f
            backPreviewStartValue = null
            backPreviewTargetValue = null
            setBackPreviewProgress(0f)
            return
        }

        if (backPreviewBaseOffsetPx.isNaN()) backPreviewBaseOffsetPx = currentPos

        val desiredOffset = (targetPos - backPreviewBaseOffsetPx) *
                (clamped * predictiveBackGesturePreviewCoefficient)
        anchoredDraggableState.previewToOffset(backPreviewBaseOffsetPx + desiredOffset)
        backPreviewAppliedOffsetPx = desiredOffset
        setBackPreviewProgress(clamped)
    }

    internal fun consumeBackPreviewStart(): CardSheetValue {
        val value = backPreviewStartValue ?: currentValue
        backPreviewStartValue = null
        backPreviewTargetValue = null
        backPreviewBaseOffsetPx = Float.NaN
        backPreviewAppliedOffsetPx = 0f
        return value
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        private fun anchorPos(value: CardSheetValue) =
            anchoredDraggableState.anchors.positionOf(value)

        private var skipNextFling = false

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

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y != 0f) {
                skipNextFling = true
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (skipNextFling || consumed.y != 0f) {
                val currentPx = anchoredDraggableState.offset
                val closest = anchoredDraggableState.anchors.closestAnchor(currentPx)
                if (closest != null) {
                    val closestPx = anchoredDraggableState.anchors.positionOf(closest)
                    val needsSettle = !currentPx.isNaN() && !closestPx.isNaN() && kotlin.math.abs(currentPx - closestPx) > 0.5f
                    if (needsSettle) {
                        handleFling(0f)
                    }
                }
                skipNextFling = false
                return Velocity.Zero
            }
            handleFling(available.y)
            return Velocity.Zero
        }
    }
}

@Composable
fun DraggableCardSheet(
    modifier: Modifier,
    state: DraggableCardSheetState,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
    cardContent: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val systemBar = WindowInsets.systemBars.asPaddingValues()

    SheetBackHandler(
        enabled = state.currentValue != CardSheetValue.Collapsed,
        onProgress = { state.applyBackPreview(it) },
        onBack = { lastProgress ->
            state.applyBackPreview(lastProgress)
            scope.launch {
                state.setBackPreviewProgress(0f)
                when (state.consumeBackPreviewStart()) {
                    CardSheetValue.Expanded ->
                        if (state.hasSemiExpanded) state.semiExpand() else state.collapse()
                    CardSheetValue.SemiExpanded -> state.collapse()
                    CardSheetValue.Collapsed -> Unit
                }
            }
        },
    )

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
            top = systemBar.calculateTopPadding() * state.expandedProgress + 16.dp * state.collapsedProgress * (1-state.expandedProgress),
            bottom = systemBar.calculateBottomPadding() * (if (state.hasSemiExpanded) state.collapsedProgress else state.progress)
        )
    }

    val sheetHeight by derivedStateOf {
        val anchors = state.anchoredDraggableState.anchors
        val collapsedPos = anchors.positionOf(CardSheetValue.Collapsed)
        val semiPos = anchors.positionOf(CardSheetValue.SemiExpanded)
        val expandedPos = anchors.positionOf(CardSheetValue.Expanded)
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
        content(
            PaddingValues(
                top = 0.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = sheetHeight + bottomContainerPadding
            )
        )

        val anchors: (IntSize, Constraints) -> Pair<DraggableAnchors<CardSheetValue>, CardSheetValue> = { _, constraints ->
            val fullHeight = constraints.maxHeight.toFloat()
            val collapsedPx = with(state.density) { state.collapsedHeight.toPx() }
            val collapsedContainerPaddingPx = with(state.density) { collapsedContainerPadding.toPx() }
            val semiContainerPaddingPx = with(state.density) { semiContainerPadding.toPx() }
            val expandedPx = with(state.density) { state.expandedHeight.toPx() }
            val newAnchors = DraggableAnchors {
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

        val bottomRadius = if (LocalInspectionMode.current) 16.dp else getBottomBorderRadius()
        val shape = RoundedCornerShape(
            topEnd = (bottomRadius - horizontalContainerPadding).coerceAtLeast(16.dp),
            topStart = (bottomRadius - horizontalContainerPadding).coerceAtLeast(16.dp),
            bottomEnd = (bottomRadius - horizontalContainerPadding).coerceAtLeast(16.dp),
            bottomStart = (bottomRadius - horizontalContainerPadding).coerceAtLeast(16.dp),
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
                        startDragImmediately = false,
                        onDragStarted = { state.setUserDragging(true) },
                        onDragStopped = { velocity ->
                            state.setUserDragging(false)
                            scope.launch { state.handleFling(velocity) }
                        },
                    )
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
    initialValue: CardSheetValue = CardSheetValue.Collapsed,
): DraggableCardSheetState {
    val density = LocalDensity.current
    return remember(density, expandedHeight, semiExpandedHeight, collapsedHeight) {
        DraggableCardSheetState(
            expandedHeight = expandedHeight,
            semiExpandedHeight = semiExpandedHeight,
            collapsedHeight = collapsedHeight,
            initialValue = initialValue,
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
