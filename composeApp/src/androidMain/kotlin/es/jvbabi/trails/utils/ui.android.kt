package es.jvbabi.trails.utils

import androidx.compose.ui.unit.Dp
import es.jvbabi.trails.MainActivity

actual fun getBottomBorderRadius(): Dp {
    return MainActivity.cornerRadiusBottom.value
}