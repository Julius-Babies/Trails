package es.jvbabi.trails.data

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import es.jvbabi.trails.config.ApplicationConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader

class DeviceInformationRepository: Closeable, KoinComponent {

    private val applicationConfig by inject<ApplicationConfig>()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    override fun close() {
        httpClient.close()
    }

    suspend fun getDeviceInformation(manufacturer: String, model: String): DeviceInformationData {
        var friendlyName = model

        if (manufacturer.equals("Apple", ignoreCase = true)) {
            val appleListResponse = httpClient.get(URLBuilder("https://raw.githubusercontent.com/kyle-seongwoo-jun/apple-device-identifiers/main/ios-device-identifiers.json").buildString())
            val appleList = appleListResponse.bodyAsText()
            val appleListJson = Json.decodeFromString<Map<String, String>>(appleList)
            friendlyName = appleListJson[model] ?: model
        } else {
            httpClient
                .prepareGet("https://storage.googleapis.com/play_public/supported_devices.csv")
                .execute { response ->
                    if (!response.status.isSuccess()) return@execute
                    val inputStream = response
                        .bodyAsChannel()
                        .toInputStream()

                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_16LE))

                    var line: String? = reader.readLine()
                    while (line != null) {
                        val cleanLine = line.removePrefix("\uFEFF")

                        if (cleanLine.isBlank()) {
                            line = reader.readLine()
                            continue
                        }

                        try {
                            val csvLine = csvReader().readAll(cleanLine).firstOrNull()
                            if (csvLine != null) {
                                if (csvLine[0].equals(manufacturer, ignoreCase = true) && (csvLine[2].equals(model, ignoreCase = true) || csvLine[3].equals(model, ignoreCase = true))) {
                                    friendlyName = csvLine[1]
                                    return@execute
                                }
                            }
                        } catch (e: Exception) {
                            println("Fehler in Zeile: $cleanLine")
                            e.printStackTrace()
                        }

                        line = reader.readLine()
                    }
                }
        }


        val response = httpClient.get(URLBuilder("https://mobile-specs-api-sandy.vercel.app/").apply {
            appendPathSegments("search")
            parameters.append("query", "$manufacturer $friendlyName")
        }.buildString())

        if (!response.status.isSuccess()) return DeviceInformationData(
            manufacturer = manufacturer,
            model = model,
            friendlyName = friendlyName,
            imageUrl = null
        )

        val responseData: Response = response.body()
        val imageUrl = responseData.data.firstOrNull()?.imageUrl

        if (imageUrl != null) {
            val deviceImagePath = applicationConfig.deviceImages.resolve("$manufacturer-$model.jpg")
            if (!deviceImagePath.exists()) {
                val imageResponse = httpClient.prepareGet(imageUrl).execute()
                if (imageResponse.status.isSuccess()) {
                    imageResponse.bodyAsChannel().toInputStream().use { input ->
                        deviceImagePath.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        return DeviceInformationData(
            manufacturer = manufacturer,
            model = model,
            friendlyName = friendlyName,
            imageUrl = imageUrl
        )
    }
}

@Serializable
private data class Response(
    @SerialName("data") val data: List<Data>
) {
    @Serializable
    data class Data(
        @SerialName("imageUrl") val imageUrl: String,
    )
}

data class DeviceInformationData(
    val manufacturer: String,
    val model: String,
    val friendlyName: String,
    val imageUrl: String?,
)