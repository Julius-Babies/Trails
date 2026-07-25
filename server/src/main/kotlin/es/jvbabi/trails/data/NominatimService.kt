package es.jvbabi.trails.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.config.ApplicationConfigFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Reverse-geocodes coordinates into a human-readable address via OpenStreetMap
 * Nominatim.
 *
 * The instance is configurable via `.nominatim.base_url` and defaults to the
 * public OSM instance.
 */
class NominatimService : ReverseGeocoding, KoinComponent {

    private val applicationConfig by inject<ApplicationConfig>()
    private val baseUrl = applicationConfig.nominatimBaseUrl

    // Only the public instance is rate limited, so caching is only worthwhile
    // there. A self-hosted instance can be queried freely, so we skip the cache
    // to always return fresh results.
    private val usePublicInstance = baseUrl == ApplicationConfigFile.Nominatim.DEFAULT_BASE_URL

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // The public Nominatim instance is rate limited (max 1 req/s) and returns
    // the same address for nearby coordinates, so we cache results by rounded
    // coordinate to avoid hammering it on every snapshot. ConcurrentHashMap
    // rejects null values, so an empty Optional encodes "looked up, no result";
    // a missing key means "not looked up yet".
    private val cache = ConcurrentHashMap<String, Optional<GeocodedAddress>>()

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddress? {
        val cacheKey = "%.5f,%.5f".format(latitude, longitude)
        if (usePublicInstance) cache[cacheKey]?.let { return it.orElse(null) }

        val response = try {
            httpClient.get(URLBuilder(baseUrl).apply {
                appendPathSegments("reverse")
                parameters.append("lat", latitude.toString())
                parameters.append("lon", longitude.toString())
                parameters.append("format", "jsonv2")
                parameters.append("addressdetails", "1")
            }.buildString()) {
                // Nominatim's usage policy requires an identifying User-Agent.
                header(HttpHeaders.UserAgent, "TrailsApp/1.0 (trails)")
                header(HttpHeaders.AcceptLanguage, "de")
            }
        } catch (e: Exception) {
            // Network errors are not cached so we retry on the next snapshot.
            return null
        }

        if (!response.status.isSuccess()) {
            if (usePublicInstance) cache[cacheKey] = Optional.empty()
            return null
        }

        val body = response.body<ReverseResponse>()
        val address = body.address?.let { addr ->
            GeocodedAddress(
                road = addr.road,
                houseNumber = addr.houseNumber,
                postcode = addr.postcode,
                city = addr.city
                    ?: addr.town
                    ?: addr.municipality
                    ?: addr.suburb
                    ?: addr.hamlet
                    ?: addr.village,
                state = addr.state,
                country = addr.country,
                displayName = body.displayName,
            )
        }
        if (usePublicInstance) cache[cacheKey] = Optional.ofNullable(address)
        return address
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    private data class ReverseResponse(
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("address") val address: Address? = null,
    ) {
        @Serializable
        data class Address(
            @SerialName("house_number") val houseNumber: String? = null,
            @SerialName("road") val road: String? = null,
            @SerialName("postcode") val postcode: String? = null,
            @SerialName("city") val city: String? = null,
            @SerialName("town") val town: String? = null,
            @SerialName("municipality") val municipality: String? = null,
            @SerialName("village") val village: String? = null,
            @SerialName("hamlet") val hamlet: String? = null,
            @SerialName("suburb") val suburb: String? = null,
            @SerialName("state") val state: String? = null,
            @SerialName("country") val country: String? = null,
        )
    }
}
