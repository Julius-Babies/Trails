package es.jvbabi.trails.page.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.ThemeWrapper
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.shapes
import trails.app.shared.generated.resources.smartphone
import trails.app.shared.generated.resources.users

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    selectedTab: HomeState.Tab,
    draggableCardSheetState: DraggableCardSheetState?,
    onSelect: (HomeState.Tab) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Item(
                icon = painterResource(Res.drawable.smartphone),
                label = "Geräte",
                isSelected = selectedTab == HomeState.Tab.MyDevices,
                draggableCardSheetState = draggableCardSheetState,
                onSelect = { onSelect(HomeState.Tab.MyDevices) }
            )

            Item(
                icon = painterResource(Res.drawable.shapes),
                label = "Gegenstände",
                isSelected = selectedTab == HomeState.Tab.Things,
                draggableCardSheetState = draggableCardSheetState,
                onSelect = { onSelect(HomeState.Tab.Things) }
            )

            Item(
                icon = painterResource(Res.drawable.users),
                label = "Freigaben",
                isSelected = selectedTab == HomeState.Tab.Shares,
                draggableCardSheetState = draggableCardSheetState,
                onSelect = { onSelect(HomeState.Tab.Shares) }
            )
        }
    }
}

@Composable
private fun RowScope.Item(
    icon: Painter,
    label: String,
    isSelected: Boolean,
    draggableCardSheetState: DraggableCardSheetState?,
    onSelect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(6.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    onSelect()
                    if (draggableCardSheetState == null) return@clickable
                    if (draggableCardSheetState.targetValue == CardSheetValue.Collapsed) {
                        scope.launch { draggableCardSheetState.semiExpand() }
                        return@clickable
                    }
                    if (!isSelected) return@clickable
                    if (draggableCardSheetState.targetValue == CardSheetValue.SemiExpanded)
                        scope.launch { draggableCardSheetState.expand() }
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        val contentColor by animateColorAsState(when (isSelected) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.onSurfaceVariant
        })

        Icon(
            painter = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )

        val fontWeight by animateIntAsState(
            when (isSelected) {
                true -> FontWeight.ExtraBold.weight
                false -> MaterialTheme.typography.labelMedium.fontWeight?.weight ?: FontWeight.Normal.weight
            }
        )

        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(fontWeight))
        )
    }
}

@Preview
@PreviewWrapper(wrapper = ThemeWrapper::class)
@Composable
fun NavigationBarPreview() {
    NavigationBar(
        selectedTab = HomeState.Tab.MyDevices,
        draggableCardSheetState = null,
        onSelect = {}
    )
}