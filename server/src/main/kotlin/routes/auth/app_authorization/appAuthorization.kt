package es.jvbabi.trails.routes.auth.app_authorization

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.trails.auth.TrailsAuthentiktUser
import es.jvbabi.trails.auth.deviceNameAttribute
import es.jvbabi.trails.config.ApplicationConfig
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.appAuthorization() {
    get {
        val authentikt by inject<AuthentiktInstance<TrailsAuthentiktUser>>()
        val applicationConfig by inject<ApplicationConfig>()
        val session = authentikt.createNewSession()

        val deviceName = call.parameters["device_name"] ?: "Unknown TrailsApp"
        session.publicAttributes[deviceNameAttribute] = deviceName

        val destination = URLBuilder(applicationConfig.url).apply {
            appendPathSegments("auth", "authorize")
            parameters.append("session_id", session.sessionId)
        }.buildString()

        call.respondRedirect(destination)
    }
}