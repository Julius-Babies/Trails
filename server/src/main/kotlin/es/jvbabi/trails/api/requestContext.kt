package es.jvbabi.trails.api

import es.jvbabi.trails.data.FEDERATION_ACTIVE_SHARE_HEADER
import es.jvbabi.trails.data.RequestContext
import io.ktor.server.application.ApplicationCall

/**
 * Build the federation [RequestContext] from an incoming request — reads the
 * capability the calling server presented in the [FEDERATION_ACTIVE_SHARE_HEADER].
 * Absent header → an empty context (no capability presented).
 */
fun ApplicationCall.requestContext(): RequestContext =
    RequestContext(activeShareId = request.headers[FEDERATION_ACTIVE_SHARE_HEADER])
