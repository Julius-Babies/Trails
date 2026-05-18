package es.jvbabi.trails.di

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.data.DeviceInformationRepository
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.ShareSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

private val module = module {
    single { DatabaseManager() }
    single { ApplicationConfig() }
    single { DeviceInformationRepository() }
    single { DeviceSubscriptionRepository() }
    single { ShareSubscriptionRepository() }
}

fun Application.installKoin() {
    install(Koin) {
        modules(module)
    }

    monitor.subscribe(ApplicationStopping) {
        val deviceInformationRepository by inject<DeviceInformationRepository>()
        deviceInformationRepository.close()
    }
}