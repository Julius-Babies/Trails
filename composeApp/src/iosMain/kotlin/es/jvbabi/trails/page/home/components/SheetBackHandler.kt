package es.jvbabi.trails.page.home.components

import androidx.compose.runtime.Composable

@Composable
internal actual fun SheetBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: (Float) -> Unit,
) {
}
