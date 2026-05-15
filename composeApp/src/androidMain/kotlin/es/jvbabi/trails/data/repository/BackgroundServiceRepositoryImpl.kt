package es.jvbabi.trails.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat.startForegroundService
import es.jvbabi.trails.android.AndroidLocationService
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackgroundServiceRepositoryImpl: BackgroundServiceRepository, KoinComponent {

    private val context by inject<Context>()
    private val serviceIntent = Intent(context, AndroidLocationService::class.java)

    override fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    override fun stopService() {
        context.stopService(serviceIntent)
    }

    override fun isRunning(): Flow<Boolean> {
        return AndroidLocationService.isRunning
    }
}