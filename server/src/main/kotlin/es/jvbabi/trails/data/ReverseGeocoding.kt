package es.jvbabi.trails.data

import java.io.Closeable

/**
 * Resolves coordinates into a human-readable address. Implementations may back
 * onto different providers (e.g. [NominatimService]).
 */
interface ReverseGeocoding : Closeable {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddress?

    override fun close() {}
}

data class GeocodedAddress(
    val road: String?,
    val houseNumber: String?,
    val postcode: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val displayName: String?,
) {
    /**
     * A label like "Musterstraße 12, Leipzig, Sachsen, Deutschland" built from
     * the available components, falling back to the full display name.
     */
    val shortLabel: String
        get() {
            val street = listOfNotNull(road, houseNumber).joinToString(" ").ifBlank { null }
            val parts = listOfNotNull(street, city, state, country)
            return parts.joinToString(", ").ifBlank { null } ?: displayName ?: "Unbekannter Ort"
        }
}
