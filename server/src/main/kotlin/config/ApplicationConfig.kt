package es.jvbabi.trails.config

import io.ktor.http.URLBuilder
import java.io.File

class ApplicationConfig(
    baseUrl: String = "https://trails.werkbank.space",
    storageDirectory: String = "./data"
) {
    val url = URLBuilder(baseUrl)
    val storage = File(storageDirectory).apply { mkdirs() }
    val deviceImages = this.storage.resolve("device-images").apply { mkdirs() }
}