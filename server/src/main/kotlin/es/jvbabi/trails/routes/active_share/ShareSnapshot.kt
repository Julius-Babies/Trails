package es.jvbabi.trails.routes.active_share

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.data.ReverseGeocoding
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.DatabaseManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * A shared device's current state, keyed by an active-share id. Honours the
 * share's settings (battery is withheld unless the share opted in).
 */
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

/**
 * Builds the current snapshot for an active share, or `null` if the share (or its
 * device) no longer exists. Reverse geocoding runs outside the DB transaction so
 * the network call doesn't hold a connection.
 */
suspend fun buildShareSnapshot(
    db: DatabaseManager,
    reverseGeocoding: ReverseGeocoding,
    activeShareId: Uuid,
): ShareSnapshotResponse? {
    val base = db.transaction {
        val activeShare = ActiveShare.findById(activeShareId) ?: return@transaction null
        val share = activeShare.share
        val device = share.device
        if (device.deletion != null) return@transaction null

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
    } ?: return null

    val enriched = base.lastLocation?.let { location ->
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

    return base.copy(lastLocation = enriched)
}

/** Resolves the device backing an active share, for live-update subscriptions. */
suspend fun activeShareDeviceId(db: DatabaseManager, activeShareId: Uuid): Uuid? =
    db.transaction {
        val device = ActiveShare.findById(activeShareId)?.share?.device ?: return@transaction null
        if (device.deletion != null) null else device.id.value
    }
