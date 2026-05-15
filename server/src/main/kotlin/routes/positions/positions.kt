package routes.positions

import es.jvbabi.trails.api.TRAILS_USER_REALM
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket

fun Route.positions() {
    authenticate(TRAILS_USER_REALM) {
        webSocket {

        }

        get {
            val user = call.principal<UserIdPrincipal>()
            call.respond("Hi, $user")
        }
    }
}