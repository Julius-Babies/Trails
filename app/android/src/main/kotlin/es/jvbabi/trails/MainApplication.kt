package es.jvbabi.trails

import android.app.Application
import android.util.Log
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.PermissionsControllerImpl
import es.jvbabi.trails.MainActivity.Companion.cornerRadiusBottom
import es.jvbabi.trails.MainActivity.Companion.isVisible
import es.jvbabi.trails.data.repository.AndroidDeviceRepository
import es.jvbabi.trails.data.repository.AndroidFileRepositoryImpl
import es.jvbabi.trails.data.repository.BackgroundServiceRepositoryImpl
import es.jvbabi.trails.di.initKoin
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.data.repository.ApplicationRepositoryImpl
import es.jvbabi.trails.utils.KOIN_KEY_CORNER_RADIUS
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("Trails", "Uncaught exception on thread: ${thread.name}", throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        initKoin {
            androidContext(this@MainApplication)
            androidLogger()

            modules(module {
                single { AndroidDeviceRepository() } bind DeviceRepository::class
                single<PermissionsController> { PermissionsControllerImpl(get()) }
                single<BackgroundServiceRepository> { BackgroundServiceRepositoryImpl() }
                single<FileRepository> { AndroidFileRepositoryImpl(get()) }
                single<ApplicationRepository> { ApplicationRepositoryImpl(get(named(ApplicationRepositoryImpl.KOIN_KEY_APP_IN_FOREGROUND_FLOW))) }
                single(named(KOIN_KEY_CORNER_RADIUS)) { cornerRadiusBottom.asStateFlow() }
                single<StateFlow<Boolean>>(named(ApplicationRepositoryImpl.KOIN_KEY_APP_IN_FOREGROUND_FLOW)) { isVisible.asStateFlow() }
            })
        }
    }
}
