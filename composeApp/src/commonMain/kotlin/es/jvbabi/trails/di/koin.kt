package es.jvbabi.trails.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.converter.UuidConverter
import es.jvbabi.trails.data.repository.DevicesRepositoryImpl
import es.jvbabi.trails.data.repository.KeyValueRepositoryImpl
import es.jvbabi.trails.data.repository.LocationRepositoryImpl
import es.jvbabi.trails.data.repository.ShareRepositoryImpl
import es.jvbabi.trails.data.repository.SnapshotRepositoryImpl
import es.jvbabi.trails.data.repository.TrailsServerRepositoryImpl
import es.jvbabi.trails.data.repository.UserRepositoryImpl
import es.jvbabi.trails.domain.repository.DevicesRepository
import es.jvbabi.trails.domain.repository.KeyValueRepository
import es.jvbabi.trails.domain.repository.LocationRepository
import es.jvbabi.trails.domain.repository.ShareRepository
import es.jvbabi.trails.domain.repository.SnapshotRepository
import es.jvbabi.trails.domain.repository.TrailsServerRepository
import es.jvbabi.trails.domain.repository.UserRepository
import es.jvbabi.trails.domain.usecase.auth.HandleDeepLinkUseCase
import es.jvbabi.trails.domain.usecase.auth.LoginUseCase
import es.jvbabi.trails.domain.usecase.communication.StartExternalConnectionsUseCase
import es.jvbabi.trails.domain.usecase.communication.StopExternalConnectionsUseCase
import es.jvbabi.trails.page.home.HomeViewModel
import es.jvbabi.trails.page.setings.SettingsViewModel
import es.jvbabi.trails.page.shares.add_share.AddShareViewModel
import es.jvbabi.trails.page.shares.new_share.NewShareViewModel
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

expect fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase>
expect fun HttpClientConfig<CIOEngineConfig>.configureHttpClient()

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()

    modules(module {
        single {
            getDatabaseBuilder()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addTypeConverter(UuidConverter())
                .build()
        }

        single<HttpClient> { HttpClient(CIO) {
            val jsonInstance = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }

            install(ContentNegotiation) {
                json(jsonInstance)
            }

            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(jsonInstance)
            }

            configureHttpClient()
        } }

        singleOf(::KeyValueRepositoryImpl) bind KeyValueRepository::class
        singleOf(::LocationRepositoryImpl) bind LocationRepository::class
        singleOf(::DevicesRepositoryImpl) bind DevicesRepository::class
        singleOf(::UserRepositoryImpl) bind UserRepository::class
        singleOf(::SnapshotRepositoryImpl) bind SnapshotRepository::class
        singleOf(::TrailsServerRepositoryImpl) bind TrailsServerRepository::class
        singleOf(::ShareRepositoryImpl) bind ShareRepository::class

        singleOf(::HandleDeepLinkUseCase)
        singleOf(::LoginUseCase)

        singleOf(::StartExternalConnectionsUseCase)
        singleOf(::StopExternalConnectionsUseCase)

        viewModelOf(::HomeViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::NewShareViewModel)
        viewModelOf(::AddShareViewModel)
    })
}