package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.page.home.HomeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import trails.app.shared.generated.resources.Res
import trails.app.shared.generated.resources.shapes
import trails.app.shared.generated.resources.smartphone
import trails.app.shared.generated.resources.users

@Composable
fun NavigationBar(
    selectedTab: HomeState.Tab,
    draggableCardSheetState: DraggableCardSheetState,
    onSelect: (HomeState.Tab) -> Unit
) {
    NavigationBar(
        containerColor = Color.Transparent,
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = Modifier.fillMaxSize()
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

@Composable
private fun RowScope.Item(
    icon: Painter,
    label: String,
    isSelected: Boolean,
    draggableCardSheetState: DraggableCardSheetState,
    onSelect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    NavigationBarItem(
        selected = isSelected && (draggableCardSheetState.targetValue != CardSheetValue.Collapsed || draggableCardSheetState.isUserDragging),
        onClick = {
            onSelect()
            if (draggableCardSheetState.targetValue == CardSheetValue.Collapsed) {
                scope.launch { draggableCardSheetState.semiExpand() }
                return@NavigationBarItem
            }
            if (!isSelected) return@NavigationBarItem
            if (draggableCardSheetState.targetValue == CardSheetValue.SemiExpanded)
                scope.launch { draggableCardSheetState.expand() }
        },
        icon = {
            Icon(
                painter = icon,
                contentDescription = null,
            )
        },
        label = { Text(label) }
    )
}