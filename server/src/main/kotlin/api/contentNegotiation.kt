package es.jvbabi.trails.api

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

val jsonInstance = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        json(jsonInstance)
    }
}