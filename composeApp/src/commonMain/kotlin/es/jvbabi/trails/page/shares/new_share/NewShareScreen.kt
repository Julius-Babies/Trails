package es.jvbabi.trails.page.shares.new_share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.jvbabi.trails.page.home.bottomFadeOut
import es.jvbabi.trails.page.home.components.PaddingValues
import es.jvbabi.trails.page.home.components.padding
import es.jvbabi.trails.utils.toDp
import org.jetbrains.compose.resources.painterResource
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.link
import trails.composeapp.generated.resources.x

@Composable
fun NewShareScreen(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    close: () -> Unit,
) {
    NewShareContent(
        contentPadding = contentPadding,
        nestedScrollConnection = nestedScrollConnection,
        close = close,
    )
}

@Composable
fun NewShareContent(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection?,
    close: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(contentPadding.copy(bottom = 0.dp))
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val titleFont = MaterialTheme.typography.headlineMedium
            Text(
                text = "Neue Freigabe erstellen",
                style = titleFont,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .height(titleFont.lineHeight.toDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = close,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.x),
                        contentDescription = "Schließen"
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, true)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomFadeOut()
                    .let { if (nestedScrollConnection != null) it.nestedScroll(nestedScrollConnection) else it }
                    .verticalScroll(rememberScrollState())
            ) {
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.link),
                        contentDescription = null,
                    )
                    Text("Link teilen")
                }
            }
        }
    }
}

@Composable
@Preview
fun NewShareScreenPreview() {
    NewShareContent(
        contentPadding = PaddingValues(),
        nestedScrollConnection = null,
        close = {},
    )
}