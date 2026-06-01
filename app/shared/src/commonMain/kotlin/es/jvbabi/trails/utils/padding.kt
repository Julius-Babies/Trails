package es.jvbabi.trails.utils

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

data class PaddingValues(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
) {
    constructor(all: Dp) : this(all, all, all, all)
    constructor(): this(0.dp)

    fun assureNonNegative() = PaddingValues(
        start = this.start.coerceAtLeast(0.dp),
        top = this.top.coerceAtLeast(0.dp),
        end = this.end.coerceAtLeast(0.dp),
        bottom = this.bottom.coerceAtLeast(0.dp)
    )

    operator fun plus(other: PaddingValues): PaddingValues = PaddingValues(
        start = this.start + other.start,
        top = this.top + other.top,
        end = this.end + other.end,
        bottom = this.bottom + other.bottom,
    )
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