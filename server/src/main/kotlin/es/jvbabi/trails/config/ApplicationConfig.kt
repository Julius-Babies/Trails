package es.jvbabi.trails.config

import es.jvbabi.trails.api.jsonInstance
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.random.Random

class ApplicationConfig(
    storageDirectory: String = "./data",
) {

    private val baseUrl: String
    val databaseUrl: String
    init {
        val configFile = File(storageDirectory).resolve("config.json")
        val configContent = configFile.readText()
        val config = jsonInstance.decodeFromString<ApplicationConfigFile>(configContent)
        baseUrl = config.baseUrl
        databaseUrl = config.databaseUrl ?: "jdbc:sqlite:${storageDirectory}/database.db"
    }

    val url = URLBuilder(baseUrl)
    val storage = File(storageDirectory).apply { mkdirs() }
    val deviceImages = this.storage.resolve("device-images").apply { mkdirs() }
    val jwtSecret = storage.resolve("jwt-secret.key")
        .let { file ->
            if (!file.exists()) file.writeText(Random.nextBytes(32).joinToString(separator = "") { "%02x".format(it) })
            file.readText()
        }
}

@Serializable
data class ApplicationConfigFile(
    @SerialName("base_url") val baseUrl: String,
    @SerialName("database_url") val databaseUrl: String? = null,
)