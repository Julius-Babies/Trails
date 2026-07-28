package es.jvbabi.trails.api

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

/**
 * Marks every response as coming from a Trails server, so a client can tell an
 * actual homeserver apart from anything else answering at the same URL (a captive
 * portal, a parked domain, …). Readable cross-origin because [installCors]
 * exposes it.
 */
const val TRAILS_ORIGIN_HEADER = "X-Trails-Origin"

fun Application.installDefaultHeaders() {
    install(DefaultHeaders) {
        header(TRAILS_ORIGIN_HEADER, "trails")
    }
}
