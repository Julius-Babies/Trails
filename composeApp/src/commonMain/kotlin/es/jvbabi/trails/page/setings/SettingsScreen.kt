@file:OptIn(ExperimentalMaterial3Api::class)

package es.jvbabi.trails.page.setings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.arrow_left

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    var showLoginDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left),
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            Button(onClick = { showLoginDialog = true }) {
                Text("Anmelden")
            }
        }
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("Anmelden") },
            text = { Column {

                var homeServerDomain by rememberSaveable { mutableStateOf("") }

                TextField(
                    value = homeServerDomain,
                    onValueChange = { homeServerDomain = it },
                    label = { Text("Home Server Domain") }
                )
            }},
            confirmButton = {
                Button(onClick = { showLoginDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
@Preview
private fun SettingsPreview() {
    SettingsScreen(
        onBack = {},
    )
}