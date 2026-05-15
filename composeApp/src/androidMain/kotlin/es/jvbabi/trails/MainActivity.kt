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
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import dev.icerock.moko.permissions.PermissionsController
import es.jvbabi.trails.domain.usecase.auth.HandleDeepLinkUseCase
import es.jvbabi.trails.page.Screen
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val KOIN_ACTIVITY_CONTEXT = "koin_activity_context"

class MainActivity : ComponentActivity(), KoinComponent {

    private var startNavigation: Screen? by mutableStateOf(null)

    private val handleDeepLinkUseCase by inject<HandleDeepLinkUseCase>()
    private val permissionsController by inject<PermissionsController>()


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        loadKoinModules(module { single(named(KOIN_ACTIVITY_CONTEXT)) { this@MainActivity as Context } })
        permissionsController.bind(this)

        onNewIntent(intent)

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
            lifecycleScope.launch {
                handleDeepLinkUseCase(Url(data.toString()))
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}