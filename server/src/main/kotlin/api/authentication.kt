package es.jvbabi.trails.api

import es.jvbabi.trails.config.ApplicationConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject

const val TRAILS_USER_REALM = "trails"

fun Application.installAuthentication() {
    val applicationConfig by inject<ApplicationConfig>()

    install(Authentication) {
        jwt(name = TRAILS_USER_REALM) {
            realm = "Trails API"

        }
    }
}