package es.jvbabi.trails

import es.jvbabi.trails.auth.installAuthentikt
import es.jvbabi.trails.di.installKoin
import es.jvbabi.trails.routes.installRouting
import io.ktor.server.application.Application

fun Application.rootModule() {
    installKoin()
    installAuthentikt()
    installRouting()
}
