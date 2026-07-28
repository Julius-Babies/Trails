package es.jvbabi.trails.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Handler for long-lived background scopes.
 *
 * A [kotlinx.coroutines.SupervisorJob] keeps a failing child from cancelling its siblings, but
 * it does not stop an uncaught exception from reaching the platform's default handler — which
 * on Android terminates the process. Connectivity is expected to fail regularly (network
 * changes, server restarts, `ConnectException` on a metered link), and none of those may take
 * the tracking service down, so failures are logged and the scope stays alive.
 *
 * This is a backstop, not a substitute for handling errors where they occur: anything caught
 * here is logged at error level so it stays visible.
 */
fun backgroundExceptionHandler(tag: String): CoroutineExceptionHandler {
    val logger = Logger.withTag(tag)
    return CoroutineExceptionHandler { context, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        logger.e(throwable) { "Uncaught exception in $context: ${throwable.message}" }
    }
}
