package es.jvbabi.trails.routes.auth.webapp

import io.ktor.http.Cookie
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.webappLogout() {
    get {
        call.response.cookies.append(Cookie(
            name = "trails-webapp-token",
            value = "",
            maxAge = 0,
            path = "/",
            secure = true,
            httpOnly = true,
        ))

        call.respondRedirect("/")
    }
}