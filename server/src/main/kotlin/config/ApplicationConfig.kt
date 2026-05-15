package es.jvbabi.trails.config

import io.ktor.http.URLBuilder
import java.io.File
import kotlin.random.Random

class ApplicationConfig(
    baseUrl: String = "https://trails.werkbank.space",
    storageDirectory: String = "./data"
) {
    val url = URLBuilder(baseUrl)
    val storage = File(storageDirectory).apply { mkdirs() }
    val deviceImages = this.storage.resolve("device-images").apply { mkdirs() }
    val jwtSecret = storage.resolve("jwt-secret.key")
        .let { file ->
            if (!file.exists()) file.writeText(Random.nextBytes(32).joinToString(separator = "") { "%02x".format(it) })
            file.readText()
        }
}