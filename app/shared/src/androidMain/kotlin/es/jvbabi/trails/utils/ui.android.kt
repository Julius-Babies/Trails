package es.jvbabi.trails.utils

import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatformTools

const val KOIN_KEY_CORNER_RADIUS = "corner_radius"

actual fun getBottomBorderRadius(): Dp {
    val cornerRadiusState = KoinPlatformTools.defaultContext().get().get<StateFlow<Dp>>(named(KOIN_KEY_CORNER_RADIUS))
    return cornerRadiusState.value
}