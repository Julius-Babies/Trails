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
            // Ab Android 12 ist der Start eines location-Foreground-Service aus dem
            // Hintergrund (auch nach BOOT_COMPLETED) verboten. Nicht abstürzen.
            logger.w { "Konnte Service nach Boot nicht starten: $e" }
        }
    }
}
