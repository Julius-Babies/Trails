package es.jvbabi.trails.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Opens the whole API to every web origin. Trails is federated: a browser client
 * talks to its own homeserver *and* directly to the foreign homeservers hosting
 * the shares its user redeemed, so those cross-origin calls must be allowed from
 * any host — a homeserver cannot know its peers up front.
 *
 * Credentials are deliberately **not** allowed, so no browser sends cookies or an
 * `Authorization` header to a foreign origin: the session-authenticated endpoints
 * stay unreachable cross-origin (they answer 401 without a session) even though
 * they now carry the permissive header. The share endpoints need no credentials —
 * the unguessable active-share id in the path *is* the authorization.
 *
 * This being the *only* place that emits `Access-Control-Allow-Origin` is part of
 * the contract: a handler that sets the header itself as well leaves the response
 * with two of them, which browsers reject outright ("multiple origin not allowed").
 */
fun Application.installCors() {
    install(CORS) {
        anyHost()
        // Ktor allows GET/POST/HEAD out of the box; the rest of the verbs the API
        // uses need to be listed for their preflight to succeed.
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        // Any request header, any JSON body — the preflight has nothing to guard
        // here, since access is decided by the capability in the URL.
        allowHeaders { true }
        allowNonSimpleContentTypes = true
        // Without this a cross-origin client cannot read the marker that proves the
        // response came from a Trails server rather than from whatever else might
        // answer at that URL.
        exposeHeader(TRAILS_ORIGIN_HEADER)
        exposeHeader(HttpHeaders.ContentType)
    }
}
