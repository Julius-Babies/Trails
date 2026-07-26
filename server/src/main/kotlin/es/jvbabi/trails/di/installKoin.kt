package es.jvbabi.trails.di

import es.jvbabi.trails.ApplicationLaunchConfig
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.data.DeviceInformationRepository
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.LocalUserRepository
import es.jvbabi.trails.data.NominatimService
import es.jvbabi.trails.data.ActiveShareRepository
import es.jvbabi.trails.data.ActiveShareRepositoryProxy
import es.jvbabi.trails.data.DeviceRepository
import es.jvbabi.trails.data.DeviceRepositoryProxy
import es.jvbabi.trails.data.FederationAuthService
import es.jvbabi.trails.data.LocalActiveShareRepository
import es.jvbabi.trails.data.LocalDeviceRepository
import es.jvbabi.trails.data.LocalShareRepository
import es.jvbabi.trails.data.RemoteRepositoryStore
import es.jvbabi.trails.data.ReverseGeocoding
import es.jvbabi.trails.data.ShareRepository
import es.jvbabi.trails.data.ShareRepositoryProxy
import es.jvbabi.trails.data.UserRepository
import es.jvbabi.trails.data.UserRepositoryProxy
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.application.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

private val coreModule = module {
    single { DatabaseManager() }
    single { RemoteRepositoryStore() }
    single { LocalUserRepository() }
    singleOf(::UserRepositoryProxy) bind UserRepository::class
    single { LocalShareRepository() }
    singleOf(::ShareRepositoryProxy) bind ShareRepository::class
    single { LocalDeviceRepository() }
    singleOf(::DeviceRepositoryProxy) bind DeviceRepository::class
    single { LocalActiveShareRepository() }
    singleOf(::ActiveShareRepositoryProxy) bind ActiveShareRepository::class
    single { FederationAuthService() }
    single { DeviceInformationRepository() }
    single { DeviceSubscriptionRepository() }
    single { UserSubscriptionRepository() }
    single<ReverseGeocoding> { NominatimService() }
}

fun Application.installKoin(
    applicationLaunchConfig: ApplicationLaunchConfig,
) {
    install(Koin) {
        modules(
            module { single { ApplicationConfig(
                storageDirectory = applicationLaunchConfig.storageDirectory.absolutePath,
            )} },
            coreModule
        )
    }

    monitor.subscribe(ApplicationStopping) {
        val deviceInformationRepository by inject<DeviceInformationRepository>()
        deviceInformationRepository.close()

        val reverseGeocoding by inject<ReverseGeocoding>()
        reverseGeocoding.close()
    }
}