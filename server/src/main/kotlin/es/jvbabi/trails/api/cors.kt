package es.jvbabi.trails.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Federation is client-driven: the app and webapp read the public entity endpoints
 * (share / device / user / active-share) and open the app websocket directly on
 * *other* Trails homeservers. For the browser webapp those are cross-origin
 * requests, so any origin must be allowed to read them.
 *
 * These endpoints are unauthenticated — the unguessable ids are the capability —
 * and carry no cookies, so allowing any origin without credentials is safe.
 * Same-origin, cookie-authenticated webapp endpoints are unaffected (credentials
 * are intentionally not enabled here).
 */
fun Application.installCors() {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
}
