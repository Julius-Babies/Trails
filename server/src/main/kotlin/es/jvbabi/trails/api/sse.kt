package es.jvbabi.trails.api

import io.ktor.server.application.*
import io.ktor.server.sse.SSE

fun Application.installSse() {
    install(SSE)
}