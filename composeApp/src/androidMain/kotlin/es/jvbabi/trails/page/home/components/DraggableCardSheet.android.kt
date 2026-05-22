package es.jvbabi.trails.page.home.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun SheetBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: (Float) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) {
        var lastProgress = 0f
        var didInvokeBack = false
        try {
            it.collect { event ->
                onProgress(event.progress)
                lastProgress = event.progress
            }
            onBack(lastProgress)
            didInvokeBack = true
        } finally {
            if (!didInvokeBack) {
                onProgress(0f)
            }
        }
    }
}
