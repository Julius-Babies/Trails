package es.jvbabi.trails.routes.devices

import kotlinx.coroutines.CompletableDeferred
import kotlin.uuid.Uuid

val pendingPings = mutableMapOf<Uuid, CompletableDeferred<PingResult>>()

data class PingResult(
    val hasDeliveredNotification: Boolean
)
