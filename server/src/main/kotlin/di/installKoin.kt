package es.jvbabi.trails.di

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

private val module = module {
    single { DatabaseManager() }
    single { ApplicationConfig() }
}

fun Application.installKoin() {
    install(Koin) {
        modules(module)
    }
}