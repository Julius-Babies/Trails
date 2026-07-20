package es.jvbabi.trails.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import es.jvbabi.trails.BuildKonfig
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.database.converter.InstantConverter
import es.jvbabi.trails.data.database.converter.UuidConverter
import es.jvbabi.trails.data.repository.*
import es.jvbabi.trails.domain.repository.*
import es.jvbabi.trails.domain.usecase.SetupNotificationsUseCase
import es.jvbabi.trails.domain.usecase.auth.HandleDeepLinkUseCase
import es.jvbabi.trails.domain.usecase.auth.LoginUseCase
import es.jvbabi.trails.domain.usecase.communication.StartExternalConnectionsUseCase
import es.jvbabi.trails.domain.usecase.communication.StopExternalConnectionsUseCase
import es.jvbabi.trails.domain.usecase.home.GetHomeDeviceLocationsUseCase
import es.jvbabi.trails.page.connection_events.ConnectionEventsViewModel
import es.jvbabi.trails.page.devices.device.DeviceViewModel
import es.jvbabi.trails.page.devices.main.DevicesViewModel
import es.jvbabi.trails.page.home.HomeViewModel
import es.jvbabi.trails.page.ringing.RingingViewModel
import es.jvbabi.trails.page.setings.SettingsViewModel
import es.jvbabi.trails.page.shares.add_share.AddShareViewModel
import es.jvbabi.trails.page.shares.new_share.NewShareViewModel
import es.jvbabi.trails.ui.overlay.DeviceDeletedViewModel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.tracing.export.otlpHttpSpanExporter
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

expect fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase>

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()

    modules(module {
        single {
            getDatabaseBuilder()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addTypeConverter(UuidConverter())
                .addTypeConverter(InstantConverter())
                .build()
        }

        single<HttpClient> {
            val jsonInstance = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }

            HttpClient {
                install(ContentNegotiation) {
                    json(jsonInstance)
                }

                install(Logging) {
                    logger = object : Logger {
                        val logger = co.touchlab.kermit.Logger.withTag("HttpClient")
                        override fun log(message: String) {
                            logger.v { message }
                        }
                    }
                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
                }

                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(jsonInstance)
                    pingInterval = 10.seconds
                }

                install(SSE)
            }
        }

        single {
            createOpenTelemetry {
                tracerProvider {
                    resource {
                        setStringAttribute(ServiceAttributes.SERVICE_NAME, "trails-app")
                    }
                    export {
                        simpleSpanProcessor(
                            exporter = otlpHttpSpanExporter(
                                baseUrl = BuildKonfig.OTEL_HTTP_COLLECTOR,
                                httpClient = get<HttpClient>()
                            )
                        )
                    }
                }
            }
        }

        singleOf(::AnalyticsRepositoryImpl) bind AnalyticsRepository::class
        singleOf(::UiRepositoryImpl) bind UiRepository::class
        singleOf(::KeyValueRepositoryImpl) bind KeyValueRepository::class
        singleOf(::LocationRepositoryImpl) bind LocationRepository::class
        singleOf(::DevicesRepositoryImpl) bind DevicesRepository::class
        singleOf(::UserRepositoryImpl) bind UserRepository::class
        singleOf(::SnapshotRepositoryImpl) bind SnapshotRepository::class
        singleOf(::TrailsServerRepositoryImpl) bind TrailsServerRepository::class
        singleOf(::ShareRepositoryImpl) bind ShareRepository::class

        singleOf(::SetupNotificationsUseCase)

        singleOf(::HandleDeepLinkUseCase)
        singleOf(::LoginUseCase)

        singleOf(::StartExternalConnectionsUseCase)
        singleOf(::StopExternalConnectionsUseCase)
        singleOf(::GetHomeDeviceLocationsUseCase)

        viewModelOf(::HomeViewModel)
        viewModelOf(::RingingViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::NewShareViewModel)
        viewModelOf(::AddShareViewModel)
        viewModelOf(::DevicesViewModel)
        viewModelOf(::DeviceViewModel)
        viewModelOf(::ConnectionEventsViewModel)
        viewModelOf(::DeviceDeletedViewModel)
    })
}