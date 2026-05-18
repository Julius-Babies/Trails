package es.jvbabi.trails.routes.devices.image

import es.jvbabi.trails.config.ApplicationConfig
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.deviceImage() {
    val applicationConfig by inject<ApplicationConfig>()

    get("/{device-name}") {
        val deviceName = call.parameters["device-name"]!!
        val file = applicationConfig.deviceImages.resolve("$deviceName.jpg")
        if (!file.exists()) {
            call.respondText("image not found", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondFile(file)
    }
}