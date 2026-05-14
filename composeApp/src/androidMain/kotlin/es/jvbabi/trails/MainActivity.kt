package es.jvbabi.trails

import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import co.touchlab.kermit.Logger
import es.jvbabi.trails.di.initKoin
import es.jvbabi.trails.page.Screen
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val KOIN_ACTIVITY_CONTEXT = "koin_activity_context"

class MainActivity : ComponentActivity() {

    private var startNavigation: Screen? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onNewIntent(intent)

        loadKoinModules(module { single(named(KOIN_ACTIVITY_CONTEXT)) { this@MainActivity as Context } })

        setContent {
            App(
                startNavigation = startNavigation,
            )
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)

        val action = intent.action
        val data = intent.data

        Logger.d { "New intent: $intent" }

        if (action == Intent.ACTION_VIEW && data.toString().startsWith("trailsapp://application")) {
            Logger.d { "New intent: $data" }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}