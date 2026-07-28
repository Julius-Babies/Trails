package es.jvbabi.trails.data.remote

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.readRawBytes
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.isWebsocket

private val logger = Logger.withTag("WsHandshake")

/** An SPA error page fits comfortably; anything beyond this would only flood the log buffer. */
private const val MAX_LOGGED_BODY_CHARS = 4_000

/**
 * Logs the full response whenever a WebSocket handshake is answered with something other
 * than 101.
 *
 * Ktor's `WebSocketException` only reports the status code ("expected status code 101 but
 * was 200"). Which hop of the proxy chain actually answered is visible only in the headers
 * and the body, so both are logged here.
 *
 * The body is deliberately read via [readRawBytes]: `bodyAsText()` would run through the
 * response pipeline and hit the WebSocket check again, throwing the `WebSocketException`
 * instead of yielding the body.
 */
val WsHandshakeDiagnostics = createClientPlugin("WsHandshakeDiagnostics") {
    on(Send) { request ->
        val call = proceed(request)
        val response = call.response
        if (request.url.protocol.isWebsocket() && response.status != HttpStatusCode.SwitchingProtocols) {
            val body = runCatching { response.readRawBytes().decodeToString() }
                .getOrElse { "<body unreadable: $it>" }
            logger.e {
                buildString {
                    appendLine("Handshake returned ${response.status} instead of 101")
                    appendLine("Requested: ${request.url.buildString()}")
                    // Differs as soon as a hop redirected via 3xx and Ktor followed it.
                    appendLine("Answered:  ${response.request.url}")
                    appendLine("Response headers:")
                    response.headers.forEach { name, values ->
                        values.forEach { value -> appendLine("  $name: $value") }
                    }
                    appendLine("Body (${body.length} chars):")
                    append(body.take(MAX_LOGGED_BODY_CHARS))
                    if (body.length > MAX_LOGGED_BODY_CHARS) append("…[truncated]")
                }
            }
        }
        call
    }
}
