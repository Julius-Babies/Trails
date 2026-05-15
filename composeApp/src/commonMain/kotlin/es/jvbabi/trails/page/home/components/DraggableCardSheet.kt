package es.jvbabi.trails.page.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun DraggableCardSheet(
    modifier: Modifier,
    state: DraggableCardSheetState,
    content: @Composable () -> Unit,
    cardContent: @Composable () -> Unit,
) {

    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier,
    ) {
        content()

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(state.height.value)
                .padding(horizontal = ((1-state.progress)*8).dp)
                .padding(bottom = (1-state.progress)*(WindowInsets.safeContent.asPaddingValues().calculateBottomPadding() + 8.dp))
                .fillMaxWidth()
                .clip(RoundedCornerShape(WindowInsets.safeContent.asPaddingValues().calculateBottomPadding().coerceAtLeast(16.dp)))
                .background(MaterialTheme.colorScheme.surface)
                .nestedScroll(state.nestedScrollConnection)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch {
                                state.dragBy(dragAmount)
                            }
                        },
                        onDragEnd = {
                            val velocityY = velocityTracker.calculateVelocity().y
                            scope.launch {
                                state.handleFling(velocityY)
                            }
                        }
                    )
                }
        ) {
            cardContent()
        }
    }
}

@Composable
fun rememberDraggableCardSheetState(
    expandedHeight: Dp = 400.dp,
    collapsedHeight: Dp = 100.dp,
): DraggableCardSheetState {
    val density = LocalDensity.current
    return remember {
        DraggableCardSheetState(
            expandedHeight = expandedHeight,
            collapsedHeight = collapsedHeight,
            density = density
        )
    }
}

class DraggableCardSheetState(
    val expandedHeight: Dp,
    val collapsedHeight: Dp,
    val density: Density,
) {
    val height = Animatable(collapsedHeight, Dp.VectorConverter)

    val progress by derivedStateOf {
        ((height.value - collapsedHeight) / (expandedHeight - collapsedHeight)).coerceIn(0f, 1f)
    }

    suspend fun expand() {
        this.height.animateTo(
            targetValue = expandedHeight,
            animationSpec = spring()
        )
    }

    suspend fun collapse() {
        this.height.animateTo(
            targetValue = collapsedHeight,
            animationSpec = spring()
        )
    }

    suspend fun snapTo(value: Dp) {
        this.height.snapTo(value)
    }

    suspend fun dragBy(deltaPx: Float) {
        val deltaDp = with(density) { deltaPx.toDp() }
        val newHeight = (height.value - deltaDp).coerceIn(collapsedHeight, expandedHeight)
        height.snapTo(newHeight)
    }

    suspend fun handleFling(velocityY: Float) {
        if (abs(velocityY) > 300f) {
            if (velocityY < 0f) expand() else collapse()
        } else {
            if (progress > 0.5f) expand() else collapse()
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaDp = with(density) { available.y.toDp() }
            val currentHeight = height.value

            if (deltaDp < 0.dp && currentHeight < expandedHeight) {
                val newHeight = (currentHeight - deltaDp).coerceAtMost(expandedHeight)
                kotlinx.coroutines.runBlocking { height.snapTo(newHeight) }
                return available
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val deltaDp = with(density) { available.y.toDp() }
            val currentHeight = height.value

            if (deltaDp > 0.dp && currentHeight > collapsedHeight) {
                val newHeight = (currentHeight - deltaDp).coerceAtLeast(collapsedHeight)
                kotlinx.coroutines.runBlocking { height.snapTo(newHeight) }
                return available
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val velocityY = available.y

            if (abs(velocityY) > 300f) {
                if (velocityY < 0f) expand() else collapse()
            } else {
                if (progress > 0.5f) expand() else collapse()
            }

            return available
        }
    }
}

@Preview
@Composable
fun DraggableCardSheetPreview() {
    DraggableCardSheet(
        modifier = Modifier,
        state = rememberDraggableCardSheetState(),
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("Content")
            }
        },
        cardContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Card content")
                Text("Card content")
                Text("Card content")
                Text("Card content")
            }
        }
    )
}