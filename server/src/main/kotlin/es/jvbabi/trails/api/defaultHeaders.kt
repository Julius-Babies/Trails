package es.jvbabi.trails.api

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

fun Application.installDefaultHeaders() {
    install(DefaultHeaders) {
        header("X-Trails-Origin", "trails")
    }
}