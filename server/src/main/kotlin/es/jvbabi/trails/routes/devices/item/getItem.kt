package es.jvbabi.trails.routes.devices.item

import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.server.application.ApplicationCall
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getDevice(): Device {
    val db by inject<DatabaseManager>()
    val deviceId = parameters["deviceId"]?.let(Uuid::parseOrNull) ?: throw EntityNotFoundException("Device not found")
    return db.transaction { Device.findById(deviceId) } ?: throw EntityNotFoundException("Device not found")
}
