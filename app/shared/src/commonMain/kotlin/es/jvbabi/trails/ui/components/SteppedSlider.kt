package es.jvbabi.trails.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import es.jvbabi.trails.ThemeWrapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val RailHeight = 16.dp
private val RailInsideCornerSize = 2.dp

/** Space kept clear on both sides of the thumb so the rail never butts against it. */
private val ThumbRailGap = 6.dp

private val TickDiameter = 4.dp
private val ThumbWidth = 52.dp
private val ThumbHeight = 32.dp
private val DraggedThumbHeight = 72.dp
private val ThumbCornerSize = 8.dp

/** Height of the whole component, sized for a comfortable touch target rather than for the rail. */
private val SliderHeight = 48.dp

/**
 * Slider that selects one of [stepCount] steps and writes the selected value inside its thumb.
 *
 * The rail spans the component's full width. Material3's own slider instead shortens its track by
 * the thumb's width - hardly visible for the default 4dp handle, but it eats about half the thumb
 * width per side as soon as the thumb is wide enough to hold a label. Here only the thumb's travel
 * is inset, so the rail keeps the same margins as the content around it.
 *
 * While dragging, the thumb follows the finger without snapping; it animates to the nearest step
 * once the gesture ends, which is also when [onSelectedIndexChange] reports that step. Crossing a
 * step during the drag triggers haptic feedback.
 *
 * @param stepCount number of selectable steps; at least two.
 * @param selectedIndex the selected step, coerced into `0..stepCount - 1`.
 * @param onSelectedIndexChange called with the step the thumb settled on.
 * @param thumbLabel the text drawn inside the thumb for a step index.
 */
@Composable
fun SteppedSlider(
    stepCount: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (index: Int) -> Unit,
    thumbLabel: (index: Int) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    thumbContentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    require(stepCount >= 2) { "A stepped slider needs at least two steps, but got $stepCount" }

    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lastIndex = stepCount - 1

    // Position in step units. Between two steps while the finger is down, a whole index afterwards.
    var position by remember { mutableFloatStateOf(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    // True from the finger touching down until it lifts, drag or not: the thumb grows on contact
    // and the position belongs to the gesture for that whole time.
    var isPressed by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var committedIndex by remember { mutableIntStateOf(selectedIndex) }

    // Derived rather than read straight off [position]: the label is the only part that has to
    // recompose during a drag, and only when the thumb actually passes a step.
    val nearestIndex by remember(lastIndex) {
        derivedStateOf { position.roundToInt().coerceIn(0, lastIndex) }
    }

    // Follow changes made by someone else, but never while a gesture or its settle animation owns
    // the position: our own change comes back through [selectedIndex] only after the caller has
    // persisted it, and applying it again would drag the thumb back to where the gesture started.
    LaunchedEffect(selectedIndex, isPressed, isSettling) {
        if (isPressed || isSettling || selectedIndex == committedIndex) return@LaunchedEffect
        committedIndex = selectedIndex
        position = selectedIndex.coerceIn(0, lastIndex).toFloat()
    }

    fun updatePosition(newPosition: Float) {
        val previousIndex = position.roundToInt()
        position = newPosition.coerceIn(0f, lastIndex.toFloat())
        if (position.roundToInt() != previousIndex) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun settleOnNearestStep() {
        val targetIndex = position.roundToInt().coerceIn(0, lastIndex)
        isSettling = true
        committedIndex = targetIndex
        onSelectedIndexChange(targetIndex)
        settleJob = coroutineScope.launch {
            try {
                animate(
                    initialValue = position,
                    targetValue = targetIndex.toFloat(),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> position = value }
            } finally {
                isSettling = false
            }
        }
    }

    val thumbHeight by animateDpAsState(
        targetValue = if (isPressed) DraggedThumbHeight else ThumbHeight,
        label = "thumbHeight",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbWidthPx = with(LocalDensity.current) { ThumbWidth.toPx() }
        val travelPx = (constraints.maxWidth - thumbWidthPx).coerceAtLeast(0f)

        // Called from layout, draw and gesture callbacks, never from composition: [position]
        // changes on every frame of a drag, and reading it here would recompose the whole slider
        // - canvas, thumb and label - instead of only moving and redrawing it.
        fun fraction(): Float = position / lastIndex

        /** Step position for a touch, taken from the thumb's centre rather than its left edge. */
        fun stepAt(x: Float): Float =
            if (travelPx > 0f) (x - thumbWidthPx / 2f) / travelPx * lastIndex else 0f

        fun thumbCenterX(): Float = thumbWidthPx / 2f + travelPx * fraction()

        /** Whether a touch landed on the thumb, which is grabbed instead of jumped to. */
        fun isOnThumb(x: Float): Boolean {
            val left = thumbCenterX() - thumbWidthPx / 2f
            return x >= left && x <= left + thumbWidthPx
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                // Press and drag are one gesture rather than a tap detector in front of a
                // `draggable`: those two have to agree on who owns the pointer, and the tap
                // detector kept it, so the thumb could be jumped to but never dragged.
                .pointerInput(enabled, travelPx, lastIndex) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        settleJob?.cancel()

                        // Grabbing the thumb keeps it under the same spot of the finger; it is
                        // wide enough to hold a label, so re-centring it on touch would tug it up
                        // to half its width away. Touching the rail instead pulls the thumb over,
                        // and from there the finger holds its centre.
                        val grabbedThumb = isOnThumb(down.position.x)
                        val grabOffset =
                            if (grabbedThumb) down.position.x - thumbCenterX() else 0f
                        if (!grabbedThumb) updatePosition(stepAt(down.position.x))

                        isPressed = true
                        // Claim the gesture right away, the way Material3's slider does. Touching
                        // the slider already moves it, so letting the surrounding scroll container
                        // take the pointer back afterwards would only fight the finger.
                        down.consume()

                        // No touch slop: the thumb sits under the finger from the first frame, so
                        // there is nothing left to tell a tap and a drag apart.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            if (change.positionChanged()) {
                                updatePosition(stepAt(change.position.x - grabOffset))
                                change.consume()
                            }
                        }

                        isPressed = false
                        settleOnNearestStep()
                    }
                }
                // The settled step, not the live position: accessibility wants the value the user
                // picked, and reporting every intermediate one would recompose on every frame.
                .progressSemantics(
                    value = committedIndex.coerceIn(0, lastIndex).toFloat(),
                    valueRange = 0f..lastIndex.toFloat(),
                    steps = lastIndex - 1,
                )
                .semantics {
                    if (!enabled) disabled()
                    setProgress { targetValue ->
                        val targetIndex = targetValue.roundToInt().coerceIn(0, lastIndex)
                        position = targetIndex.toFloat()
                        committedIndex = targetIndex
                        onSelectedIndexChange(targetIndex)
                        true
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRail(
                    fraction = fraction(),
                    stepCount = stepCount,
                    thumbWidthPx = thumbWidthPx,
                    travelPx = travelPx,
                    activeColor = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor,
                    inactiveColor = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor,
                    activeTickColor = if (enabled) colors.activeTickColor else colors.disabledActiveTickColor,
                    inactiveTickColor = if (enabled) colors.inactiveTickColor else colors.disabledInactiveTickColor,
                )
            }

            // The thumb grows upwards while dragging, so it is measured at its collapsed size and
            // allowed to overflow: its footprint has to stay put for the travel to stay correct.
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = (travelPx * fraction()).roundToInt(), y = 0) }
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .wrapContentSize(align = Alignment.BottomCenter, unbounded = true),
            ) {
                Box(
                    modifier = Modifier
                        .width(ThumbWidth)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(ThumbCornerSize))
                        .background(if (enabled) colors.thumbColor else colors.disabledThumbColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AnimatedContent(
                        targetState = thumbLabel(nearestIndex),
                        transitionSpec = {
                            slideInVertically { it } togetherWith slideOutVertically { -it }
                        },
                        label = "thumbLabel",
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = thumbContentColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws the rail with a gap around the thumb, plus one tick per step.
 *
 * Ticks sit where the thumb's centre comes to rest, which is inset by half the thumb width on both
 * sides - the rail itself still runs edge to edge.
 */
private fun DrawScope.drawRail(
    fraction: Float,
    stepCount: Int,
    thumbWidthPx: Float,
    travelPx: Float,
    activeColor: Color,
    inactiveColor: Color,
    activeTickColor: Color,
    inactiveTickColor: Color,
) {
    val railHeight = RailHeight.toPx()
    val top = (size.height - railHeight) / 2f
    val bottom = top + railHeight
    val centerY = top + railHeight / 2f

    val outerCorner = CornerRadius(railHeight / 2f)
    val insideCorner = CornerRadius(RailInsideCornerSize.toPx())

    val thumbCenterX = thumbWidthPx / 2f + travelPx * fraction
    val gap = thumbWidthPx / 2f + ThumbRailGap.toPx()
    val cutoutStart = thumbCenterX - gap
    val cutoutEnd = thumbCenterX + gap

    if (cutoutStart > 0f) {
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left = 0f, top = top, right = cutoutStart, bottom = bottom),
                        topLeft = outerCorner,
                        topRight = insideCorner,
                        bottomRight = insideCorner,
                        bottomLeft = outerCorner,
                    )
                )
            },
            color = activeColor,
        )
    }

    if (cutoutEnd < size.width) {
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left = cutoutEnd, top = top, right = size.width, bottom = bottom),
                        topLeft = insideCorner,
                        topRight = outerCorner,
                        bottomRight = outerCorner,
                        bottomLeft = insideCorner,
                    )
                )
            },
            color = inactiveColor,
        )
    }

    val tickRadius = TickDiameter.toPx() / 2f
    repeat(stepCount) { index ->
        val tickX = thumbWidthPx / 2f + travelPx * index / (stepCount - 1)
        // Ticks hidden by the thumb and its gap would only flicker at the edges.
        if (tickX > cutoutStart && tickX < cutoutEnd) return@repeat

        drawCircle(
            color = if (tickX < thumbCenterX) activeTickColor else inactiveTickColor,
            radius = tickRadius,
            center = Offset(x = tickX, y = centerY),
        )
    }
}

@Preview
@PreviewLightDark
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun SteppedSliderPreview() {
    val meterValues = listOf(3, 5, 10, 30, 50, 100)
    var selectedIndex by remember { mutableIntStateOf(2) }

    Column(modifier = Modifier.padding(16.dp)) {
        SteppedSlider(
            stepCount = meterValues.size,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { selectedIndex = it },
            thumbLabel = { "${meterValues[it]}m" },
        )
    }
}
