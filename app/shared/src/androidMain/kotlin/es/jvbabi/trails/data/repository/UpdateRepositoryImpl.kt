package es.jvbabi.trails.data.repository

import android.content.Context
import android.os.Build
import es.jvbabi.trails.domain.repository.UpdateRepository

class UpdateRepositoryImpl(
    private val context: Context,
) : UpdateRepository {

    override fun canInstallUpdates(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }
}
