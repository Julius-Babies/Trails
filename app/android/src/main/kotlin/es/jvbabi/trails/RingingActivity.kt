package es.jvbabi.trails

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.jvbabi.trails.android.RingService
import es.jvbabi.trails.domain.repository.UiRepository
import es.jvbabi.trails.page.ringing.RingingScreen
import es.jvbabi.trails.page.ringing.RingingViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val deviceName = intent?.getStringExtra(RingService.EXTRA_DEVICE_NAME) ?: "Unknown"

        setContent {
            val viewModel = koinViewModel<RingingViewModel>()
            val uiRepository = koinInject<UiRepository>()
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.init(deviceName) {
                    uiRepository.sendSnackbar("Das Klingeln wurde beendet.", 5.seconds)
                    finish()
                }
            }

            LaunchedEffect(state.isRinging) {
                if (!state.isRinging) finish()
            }

            RingingScreen(viewModel = viewModel)
        }
    }
}
