package es.jvbabi.trails.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.domain.repository.Snackbar
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Composable
fun Snackbar(
    modifier: Modifier = Modifier,
    snackbar: Snackbar
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dropShadow(
                shape = RoundedCornerShape(8.dp),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = .2f),
                    spread = 1.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    radius = 8.dp,
                )
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(8.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
            Text(
                text = snackbar.title,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun SnackbarPreview() {
    Snackbar(
        snackbar = Snackbar(
            title = "Something went wrong",
            autoDismiss = 5.seconds,
            createdAt = Clock.System.now(),
        )
    )
}