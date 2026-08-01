@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.ui.overlay.update_available

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.domain.model.AppVersions
import es.jvbabi.trails.domain.model.Changelog
import es.jvbabi.trails.domain.model.issueUrl
import es.jvbabi.trails.ui.components.ProgressiveBlurScrim
import es.jvbabi.trails.ui.components.ScrimEdge
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import trails.app.shared.generated.resources.*

@Composable
fun UpdateAvailableOverlay() {
    val viewModel = koinViewModel<UpdateAvailableViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state == null) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it == SheetValue.Expanded || state!!.isDismissed }
    )

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { viewModel.onEvent(UpdateAvailableEvent.RequestDismiss) },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            UpdateAvailableOverlayContent(
                onEvent = viewModel::onEvent,
                state = state!!,
            )
        }
    }

    LaunchedEffect(state?.isDismissed) {
        if (state?.isDismissed == true) {
            sheetState.hide()
        }
    }
}

@Composable
fun UpdateAvailableOverlayContent(
    onEvent: (event: UpdateAvailableEvent) -> Unit,
    state: UpdateAvailableState,
) {
    // A version without a single entry has nothing to show, and neither has a changelog that only
    // consists of those - in that case the overlay stays exactly the prompt it was.
    val versions = state.changelog?.versions?.filterNot { it.isEmpty }.orEmpty()

    // The list is the only part that wants all the room it can get, so it takes over the slack the
    // prompt otherwise spends on centring itself.
    val showsChangelog = !state.areChangelogsLoading && versions.isNotEmpty()

    val density = LocalDensity.current
    val hazeState = rememberHazeState()

    // Measured rather than guessed: the buttons float above the changelog, so the list needs to know
    // how much room to leave below its last entry. A hard-coded value would break as soon as the
    // button text wraps or the font scale changes.
    var buttonsHeight by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            // No bottom padding: the changelog runs to the very bottom of the sheet and under the
            // scrim, which brings its own padding.
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        if (showsChangelog) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(.5f))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .size(108.dp),
            )
        }
        if (showsChangelog) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(.4f))
        Text(
            text = stringResource(Res.string.update_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.update_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = AppVersions.tagOf(state.currentVersion),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Right,
            )

            Icon(
                painter = painterResource(Res.drawable.move_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outline,
            )

            AnimatedContent(
                targetState = state.latestVersion,
                modifier = Modifier.weight(1f),
            ) { latestVersion ->
                if (latestVersion != null) {
                    Text(
                        text = AppVersions.tagOf(latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Left,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }
        when {
            // Runs to the bottom of the sheet and scrolls under the buttons, which float above it
            // on a blurred scrim. The room they take is added below the last entry instead.
            showsChangelog -> {
                Spacer(Modifier.height(24.dp))
                ChangelogList(
                    versions = versions,
                    onIssueClick = { issue ->
                        onEvent(UpdateAvailableEvent.OpenIssue(issueUrl(issue)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    bottomPadding = buttonsHeight,
                )
            }

            state.areChangelogsLoading -> {
                Spacer(Modifier.height(24.dp))
                ChangelogLoadingPlaceholder(Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1.25f))
                // Nothing scrolls here, so the prompt has to keep clear of the buttons itself.
                Spacer(Modifier.height(buttonsHeight))
            }

            else -> {
                Spacer(Modifier.weight(1.25f))
                Spacer(Modifier.height(buttonsHeight))
            }
            }
        }

        ProgressiveBlurScrim(
            hazeState = hazeState,
            edge = ScrimEdge.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { size ->
                    buttonsHeight = with(density) { size.height.toDp() }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    onClick = { onEvent(UpdateAvailableEvent.RequestDismiss) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.update_not_now)
                    )
                }
                Button(
                    onClick = { onEvent(UpdateAvailableEvent.Install) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.update_install))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun UpdateAvailableOverlayPreview() {
    UpdateAvailableOverlayContent(
        onEvent = {},
        state = UpdateAvailableState(
            isDismissed = false,
            currentVersion = "20260714_0930",
            latestVersion = "20260731_1812",
            changelog = Changelog(versions = previewChangelogVersions),
            areChangelogsLoading = false,
        )
    )
}

@Preview(showBackground = true)
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun UpdateAvailableOverlayLoadingChangelogPreview() {
    UpdateAvailableOverlayContent(
        onEvent = {},
        state = UpdateAvailableState(
            isDismissed = false,
            currentVersion = "20260714_0930",
            latestVersion = "20260731_1812",
            areChangelogsLoading = true,
        )
    )
}

@Preview(showBackground = true)
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
private fun UpdateAvailableOverlayWithoutChangelogPreview() {
    UpdateAvailableOverlayContent(
        onEvent = {},
        state = UpdateAvailableState(
            isDismissed = false,
            currentVersion = "20260714_0930",
            latestVersion = "20260731_1812",
            areChangelogsLoading = false,
        )
    )
}