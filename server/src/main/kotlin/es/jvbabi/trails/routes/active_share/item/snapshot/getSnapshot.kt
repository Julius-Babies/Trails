package es.jvbabi.trails.routes.active_share.item.snapshot

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.data.ReverseGeocoding
import es.jvbabi.trails.routes.EntityNotFoundException
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.koin.ktor.ext.inject
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * Public, capability-based snapshot of a shared device, keyed by the active-share
 * id (an unguessable UUID that acts as the capability — the same handle the app
 * subscribes with). This is what a foreign homeserver's web client fetches
 * directly to render a shared device, so it is unauthenticated and CORS-open.
 *
 * Returns the current location/battery honouring the share's settings (battery is
 * withheld unless the share opted in).
 */
fun Route.getActiveShareSnapshot() {
    val db by inject<DatabaseManager>()
    val reverseGeocoding by inject<ReverseGeocoding>()

    get {
        // Allow any web origin to read this capability endpoint (cross-homeserver
        // federation runs in the browser). A plain GET with no custom headers is a
        // CORS "simple request", so no preflight handling is required.
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")

        val activeShareId = call.parameters["activeShareId"]?.let(Uuid::parseOrNull)
            ?: throw EntityNotFoundException("Active share not found")

        val response = db.transaction {
            val activeShare = ActiveShare.findById(activeShareId) ?: throw EntityNotFoundException("Active share not found")
            val share = activeShare.share
            val device = share.device
            if (device.deletion != null) throw EntityNotFoundException("Active share not found")

            val latestSnapshot = DataSnapshot
                .find { DataSnapshots.device eq device.id }
                .orderBy(DataSnapshots.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()

            val battery = if (share.shareBatteryState) {
                val level = latestSnapshot?.batteryLevel
                val charging = latestSnapshot?.batteryCharging
                if (level != null && charging != null) {
                    ShareSnapshotResponse.Battery((level * 100).roundToInt(), charging)
                } else null
            } else null

            ShareSnapshotResponse(
                name = share.shareName,
                manufacturer = device.manufacturer,
                model = device.model,
                deviceFriendlyName = device.friendlyName,
                ownerUsername = device.owner.username,
                lastLocation = latestSnapshot?.let {
                    ShareSnapshotResponse.LastLocation(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        foundAt = it.createdAt.toEpochMilliseconds(),
                    )
                },
                battery = battery,
            )
        }

        // Reverse-geocode outside the transaction so the network call doesn't hold
        // a DB connection.
        val enriched = response.lastLocation?.let { location ->
            val address = reverseGeocoding.reverseGeocode(location.latitude, location.longitude)
                ?: return@let location
            location.copy(
                address = ShareSnapshotResponse.LastLocation.Address(
                    road = address.road,
                    houseNumber = address.houseNumber,
                    postcode = address.postcode,
                    city = address.city,
                    state = address.state,
                    country = address.country,
                    displayName = address.displayName,
                    label = address.shortLabel,
                )
            )
        }

        call.respond(response.copy(lastLocation = enriched))
    }
}

@Serializable
data class ShareSnapshotResponse(
    @SerialName("name") val name: String,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("device_friendly_name") val deviceFriendlyName: String,
    @SerialName("owner_username") val ownerUsername: String,
    @SerialName("last_location") val lastLocation: LastLocation?,
    @SerialName("battery") val battery: Battery?,
) {
    @Serializable
    data class Battery(
        @SerialName("percentage") val percentage: Int,
        @SerialName("is_charging") val isCharging: Boolean,
    )

    @Serializable
    data class LastLocation(
        @SerialName("latitude") val latitude: Double,
        @SerialName("longitude") val longitude: Double,
        @SerialName("found_at") val foundAt: Long,
        @SerialName("address") val address: Address? = null,
    ) {
        @Serializable
        data class Address(
            @SerialName("road") val road: String?,
            @SerialName("house_number") val houseNumber: String?,
            @SerialName("postcode") val postcode: String?,
            @SerialName("city") val city: String?,
            @SerialName("state") val state: String?,
            @SerialName("country") val country: String?,
            @SerialName("display_name") val displayName: String?,
            @SerialName("label") val label: String,
        )
    }
}
