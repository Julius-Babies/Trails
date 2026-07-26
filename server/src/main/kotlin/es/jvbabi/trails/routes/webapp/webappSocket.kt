package es.jvbabi.trails.routes.webapp

import database.DataSnapshot
import database.DataSnapshots
import es.jvbabi.trails.api.TRAILS_WEBAPP_REALM
import es.jvbabi.trails.api.TrailsWebappPrincipal
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.data.DeviceSubscriptionMessage
import es.jvbabi.trails.data.DeviceSubscriptionRepository
import es.jvbabi.trails.data.ReverseGeocoding
import es.jvbabi.trails.data.UserSubscriptionMessage
import es.jvbabi.trails.data.UserSubscriptionRepository
import es.jvbabi.trails.database.ActiveShare
import es.jvbabi.trails.database.ActiveShares
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.DbShare as ShareEntity
import es.jvbabi.trails.database.Shares
import es.jvbabi.trails.database.UserShare
import es.jvbabi.trails.database.UserShares
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.webappSocket() {

    val userSubscriptionRepository by inject<UserSubscriptionRepository>()
    val deviceSubscriptionRepository by inject<DeviceSubscriptionRepository>()
    val reverseGeocoding by inject<ReverseGeocoding>()
    val applicationConfig by inject<ApplicationConfig>()
    val db by inject<DatabaseManager>()

    authenticate(TRAILS_WEBAPP_REALM) {
        webSocket {
            val user = call.principal<TrailsWebappPrincipal>()!!.user

            // One collector job per subscribed device flow.
            val deviceSubscriptions = mutableMapOf<Uuid, Job>()

            // Reverse-geocode the last known location outside the DB
            // transaction so the network call doesn't hold a connection.
            suspend fun enrichLocation(
                location: WebAppSocketServerMessage.DevicesUpdate.Device.LastLocation?,
            ): WebAppSocketServerMessage.DevicesUpdate.Device.LastLocation? {
                if (location == null) return null
                val address = reverseGeocoding.reverseGeocode(location.latitude, location.longitude)
                    ?: return location
                return location.copy(
                    address = WebAppSocketServerMessage.DevicesUpdate.Device.LastLocation.Address(
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

            // Saved shares that live on THIS homeserver. Used to keep live
            // subscriptions on their devices; foreign saved shares are resolved by
            // the client directly and don't stream through this socket.
            fun resolveShares(): List<ActiveShare> =
                UserShare.find { UserShares.user eq user.id }
                    .filter { it.homeserver == applicationConfig.url.host }
                    .mapNotNull { userShare -> ActiveShare.findById(userShare.shareId) }
                    .filter { it.share.device.deletion == null }

            // Resolve the user's saved shares that live on THIS homeserver into fully
            // populated share cards (name, device, owner, location, battery). Foreign
            // saved shares are excluded — the client resolves those directly against
            // their owning homeserver (see [resolveForeignShareRefs]).
            fun resolveSavedShares(): List<WebAppSocketServerMessage.DevicesUpdate.Share> =
                db.transaction {
                    UserShare.find { UserShares.user eq user.id }
                        .filter { it.homeserver == applicationConfig.url.host }
                        .mapNotNull { userShare ->
                            val activeShare = ActiveShare.findById(userShare.shareId) ?: return@mapNotNull null
                            val share = activeShare.share
                            val device = share.device
                            if (device.deletion != null) return@mapNotNull null
                            val deviceInfo = WebAppSocketServerMessage.DevicesUpdate.Device.fromDevice(device)
                            WebAppSocketServerMessage.DevicesUpdate.Share(
                                id = userShare.id.value,
                                name = share.shareName,
                                deviceDisplayName = device.displayName,
                                ownerUsername = device.owner.username,
                                manufacturer = device.manufacturer,
                                model = device.model,
                                battery = if (share.shareBatteryState) deviceInfo.battery else null,
                                lastLocation = deviceInfo.lastLocation,
                            )
                        }
                }

            // Saved shares that live on a FOREIGN homeserver. Only the reference
            // (saved-share row id, active-share id, homeserver) is sent — the client
            // fetches the share/device/owner data and live location directly from
            // that homeserver.
            fun resolveForeignShareRefs(): List<WebAppSocketServerMessage.DevicesUpdate.ForeignShare> =
                UserShare.find { UserShares.user eq user.id }
                    .filter { it.homeserver != applicationConfig.url.host }
                    .map {
                        WebAppSocketServerMessage.DevicesUpdate.ForeignShare(
                            id = it.id.value,
                            activeShareId = it.shareId,
                            homeserver = it.homeserver,
                        )
                    }

            // Resolve the shares this user has emitted (created) themselves. A
            // share is owned via its device, so we select all shares whose device
            // belongs to the current user and whose device is not deleted.
            fun resolveEmittedShares(): List<ShareEntity> =
                ShareEntity.wrapRows(
                    Shares
                        .innerJoin(Devices)
                        .select(Shares.columns)
                        .where { Devices.owner eq user.id }
                ).filter { it.device.deletion == null }

            suspend fun sendDevices() {
                val devicesEmittedForeign = db.transaction {
                    val devices = user.devices
                        .filter { it.deletion == null }
                        .map { device -> WebAppSocketServerMessage.DevicesUpdate.Device.fromDevice(device) }
                    val emittedShares = resolveEmittedShares()
                        .map { share -> WebAppSocketServerMessage.DevicesUpdate.EmittedShare.fromShare(share) }
                    val foreignShares = resolveForeignShareRefs()
                    Triple(devices, emittedShares, foreignShares)
                }
                val (devices, emittedShares, foreignShares) = devicesEmittedForeign
                val shares = resolveSavedShares()
                sendSerialized<WebAppSocketServerMessage>(
                    WebAppSocketServerMessage.DevicesUpdate(
                        devices = devices.map { it.copy(lastLocation = enrichLocation(it.lastLocation)) },
                        shares = shares.map { it.copy(lastLocation = enrichLocation(it.lastLocation)) },
                        emittedShares = emittedShares,
                        foreignShares = foreignShares,
                    )
                )
            }

            // Subscribe to a single device flow and re-send the device list on
            // every snapshot (location / battery change).
            fun subscribeToDevice(deviceId: Uuid) {
                if (deviceSubscriptions[deviceId]?.isActive == true) return
                deviceSubscriptions[deviceId] = launch {
                    deviceSubscriptionRepository.getFlowForDeviceSubscription(deviceId)
                        .filter { it is DeviceSubscriptionMessage.Snapshot }
                        .onEach { sendDevices() }
                        .collect()
                }
            }

            // Send the current state right after connecting.
            sendDevices()

            // Subscribe to all currently owned devices.
            db.transaction { user.devices.filter { it.deletion == null }.map { it.id.value } }
                .forEach { subscribeToDevice(it) }

            // Subscribe to the underlying devices of all saved shares so their
            // location / battery changes re-send the list too.
            db.transaction { resolveShares().map { it.share.device.id.value } }
                .forEach { subscribeToDevice(it) }

            // Re-send the full device list whenever a device of this user is
            // added, changed or removed, and keep the device subscriptions in sync.
            launch {
                userSubscriptionRepository.getFlowForUser(user.id.value)
                    .filter { it is UserSubscriptionMessage.DeviceUpdated || it is UserSubscriptionMessage.DeviceDeleted }
                    .onEach { message ->
                        when (message) {
                            is UserSubscriptionMessage.DeviceUpdated ->
                                subscribeToDevice(db.transaction { message.device.id.value })
                            is UserSubscriptionMessage.DeviceDeleted -> {
                                val deviceId = db.transaction { message.deletion.device.id.value }
                                deviceSubscriptions.remove(deviceId)?.cancel()
                            }
                            else -> {}
                        }
                        sendDevices()
                    }
                    .collect()
            }

            // Keep the connection open until the client disconnects.
            for (frame in incoming) {
                // The webapp socket is server-push only; ignore inbound frames.
            }
        }
    }
}

@Serializable
sealed class WebAppSocketServerMessage {
    @SerialName("devices.update")
    @Serializable
    data class DevicesUpdate(
        @SerialName("devices") val devices: List<Device>,
        @SerialName("shares") val shares: List<Share> = emptyList(),
        @SerialName("emitted_shares") val emittedShares: List<EmittedShare> = emptyList(),
        @SerialName("foreign_shares") val foreignShares: List<ForeignShare> = emptyList(),
    ): WebAppSocketServerMessage() {
        @Serializable
        data class Device(
            @SerialName("id") val id: Uuid,
            @SerialName("manufacturer") val manufacturer: String,
            @SerialName("model") val model: String,
            @SerialName("friendly_name") val friendlyName: String,
            @SerialName("display_name") val displayName: String,
            @SerialName("battery") val battery: Battery?,
            @SerialName("last_location") val lastLocation: LastLocation?,
        ) {
            companion object {
                fun fromDevice(device: es.jvbabi.trails.database.Device): Device {
                    val latestSnapshot = DataSnapshot
                        .find { DataSnapshots.device eq device.id }
                        .orderBy(DataSnapshots.createdAt to SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()
                    return Device(
                        id = device.id.value,
                        manufacturer = device.manufacturer,
                        model = device.model,
                        friendlyName = device.friendlyName,
                        displayName = device.displayName,
                        battery = latestSnapshot?.let { snapshot ->
                            val level = snapshot.batteryLevel ?: return@let null
                            val charging = snapshot.batteryCharging ?: return@let null
                            Battery(
                                percentage = (level * 100).toInt(),
                                isCharging = charging
                            )
                        },
                        lastLocation = latestSnapshot?.let { snapshot ->
                            val latitude = snapshot.latitude
                            val longitude = snapshot.longitude
                            val foundAt = snapshot.createdAt.toEpochMilliseconds()
                            LastLocation(
                                latitude = latitude,
                                longitude = longitude,
                                foundAt = foundAt
                            )
                        }
                    )
                }
            }

            @Serializable
            data class Battery(
                @SerialName("percentage") val percentage: Int,
                @SerialName("is_charging") val isCharging: Boolean
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
         * A location share this user has saved. Resolved through the federation
         * chain (active share → share → device → owner), so it works for shares
         * that live on a foreign homeserver too. [id] is the local saved-share row
         * id (stable across local/remote). Location and battery are only populated
         * for shares whose device lives on this server — they are not federated —
         * and battery only when the share allows it. Manufacturer/model are carried
         * for the device image.
         */
        @Serializable
        data class Share(
            @SerialName("id") val id: Uuid,
            @SerialName("name") val name: String,
            @SerialName("device_display_name") val deviceDisplayName: String,
            @SerialName("owner_username") val ownerUsername: String,
            @SerialName("manufacturer") val manufacturer: String,
            @SerialName("model") val model: String,
            @SerialName("battery") val battery: Device.Battery?,
            @SerialName("last_location") val lastLocation: Device.LastLocation?,
        )

        /**
         * A saved location share that lives on a FOREIGN homeserver. The server
         * only forwards the reference; the client fetches the share/device/owner
         * data and the live location directly from [homeserver] (REST entity reads
         * + the app websocket's share subscription, using [activeShareId]).
         * [id] is the local saved-share row id (stable, used as the pin/list key).
         */
        @Serializable
        data class ForeignShare(
            @SerialName("id") val id: Uuid,
            @SerialName("active_share_id") val activeShareId: Uuid,
            @SerialName("homeserver") val homeserver: String,
        )

        /**
         * A location share this user has emitted (created) themselves. Carries
         * the share settings and how often it has been redeemed (one active-share
         * row per redemption). Manufacturer/model are only carried for the device
         * image on the frontend.
         */
        @Serializable
        data class EmittedShare(
            @SerialName("id") val id: Uuid,
            @SerialName("name") val name: String,
            @SerialName("device_id") val deviceId: Uuid,
            @SerialName("device_display_name") val deviceDisplayName: String,
            @SerialName("manufacturer") val manufacturer: String,
            @SerialName("model") val model: String,
            @SerialName("location_history_seconds") val locationHistorySeconds: Int,
            @SerialName("share_battery_state") val shareBatteryState: Boolean,
            @SerialName("allow_multiuse") val allowMultiuse: Boolean,
            @SerialName("is_locked") val isLocked: Boolean,
            @SerialName("created_at") val createdAt: Long,
            @SerialName("redemption_count") val redemptionCount: Long,
        ) {
            companion object {
                fun fromShare(share: es.jvbabi.trails.database.DbShare): EmittedShare {
                    val device = share.device
                    return EmittedShare(
                        id = share.id.value,
                        name = share.shareName,
                        deviceId = device.id.value,
                        deviceDisplayName = device.displayName,
                        manufacturer = device.manufacturer,
                        model = device.model,
                        locationHistorySeconds = share.locationHistorySeconds,
                        shareBatteryState = share.shareBatteryState,
                        allowMultiuse = share.allowMultiuse,
                        isLocked = share.isLocked,
                        createdAt = share.createdAt.toEpochMilliseconds(),
                        redemptionCount = ActiveShare.count(ActiveShares.share eq share.id),
                    )
                }
            }
        }
    }
}
