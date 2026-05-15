package routes.app

import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

fun Route.app() {
    authenticate(TRAILS_USER_REALM) {
        webSocket("/ws") {
            val user = call.principal<TrailsAppUserPrincipal>()!!
            sendSerialized("Hi, ${user.user.username}")
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    println("Received $text")
                }
            }
        }

        get {
            val user = call.principal<TrailsAppUserPrincipal>()
            call.respond("Hi, $user")
        }
    }
}