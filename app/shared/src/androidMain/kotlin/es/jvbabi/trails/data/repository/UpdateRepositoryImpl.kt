package es.jvbabi.trails.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import es.jvbabi.trails.domain.repository.UpdateRepository

class UpdateRepositoryImpl(
    private val context: Context,
) : UpdateRepository {

    override fun canInstallUpdates(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    override fun openInstallPermissionSettings() {
        val packageUri = "package:${context.packageName}".toUri()

        // The per-app screen only exists from Android 8 on, and some ROMs ship no activity for it
        // even then. The app's own settings page is the fallback in both cases: the switch is one
        // tap further in from there, which beats going nowhere.
        val unknownAppSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
        } else {
            null
        }

        val intent = unknownAppSources?.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

        // Started from outside an activity, so it needs a task of its own.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
