package es.jvbabi.trails.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import platform.UIKit.UIApplication

actual fun getBottomBorderRadius(): Dp {
    val window = UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow

    val radius = window?.rootViewController?.view?.layer?.cornerRadius ?: 0.0
    return radius.dp
}