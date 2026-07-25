package es.jvbabi.trails.routes.webapp.mapbox

import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.config.ApplicationConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.webappMapbox() {
    authenticate(TRAILS_WEBAPP_REALM) {
        get {
            val applicationConfig by inject<ApplicationConfig>()
            val accessToken = applicationConfig.mapboxAccessToken
            if (accessToken == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(WebappMapboxResponse(accessToken = accessToken))
        }
    }
}

@Serializable
data class WebappMapboxResponse(
    @SerialName("access_token") val accessToken: String,
)
