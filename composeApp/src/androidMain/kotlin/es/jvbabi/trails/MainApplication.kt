package es.jvbabi.trails

import android.app.Application
import es.jvbabi.trails.data.repository.AndroidDeviceRepository
import es.jvbabi.trails.di.initKoin
import es.jvbabi.trails.domain.repository.DeviceRepository
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.dsl.bind
import org.koin.dsl.module

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext(this@MainApplication)
            androidLogger()

            modules(module {
                single { AndroidDeviceRepository() } bind DeviceRepository::class
            })
        }
    }
}