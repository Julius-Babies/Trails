package es.jvbabi.trails.page.devices

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class Screen {
    @Serializable
    data object Main: Screen()

    @Serializable
    data class Device(val deviceId: Uuid): Screen()
}