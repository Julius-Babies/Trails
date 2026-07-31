package es.jvbabi.trails.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat.startForegroundService
import co.touchlab.kermit.Logger

class BootReceiver : BroadcastReceiver() {

    private val logger = Logger.withTag("BootReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        logger.d { "Received boot completed intent; action: ${intent.action}" }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val serviceIntent = Intent(context, AndroidLocationService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // From Android 12 on, starting a location foreground service from the background
            // is forbidden (even after BOOT_COMPLETED). Don't crash.
            logger.w { "Could not start the service after boot: $e" }
        }
    }
}
