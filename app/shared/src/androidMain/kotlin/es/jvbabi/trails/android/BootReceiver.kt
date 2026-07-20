package es.jvbabi.trails.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat.startForegroundService
import co.touchlab.kermit.Logger
import es.jvbabi.trails.domain.repository.AnalyticsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val logger = Logger.withTag("BootReceiver")
    private val analyticsRepository by inject<AnalyticsRepository>()

    override fun onReceive(context: Context, intent: Intent) {
        logger.d { "Received boot completed intent; action: ${intent.action}" }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        analyticsRepository.trackEvent("system.boot")
        val serviceIntent = Intent(context, AndroidLocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
