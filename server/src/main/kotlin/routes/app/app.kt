package routes.app

import database.DataSnapshot
import es.jvbabi.trails.api.TRAILS_USER_REALM
import es.jvbabi.trails.api.TrailsAppUserPrincipal
import es.jvbabi.trails.database.DatabaseManager
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.time.Instant

fun Route.app() {

    val db by inject<DatabaseManager>()

    authenticate(TRAILS_USER_REALM) {
        webSocket("/ws") {
            val user = call.principal<TrailsAppUserPrincipal>()!!
            sendSerialized("Hi, ${user.user.username}")
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = converter!!.deserialize<TrailsWebSocketAppMessage>(frame)
                    when (message) {
                        is TrailsWebSocketAppMessage.DataSnapshot -> {
                            launch {
                                db.transaction {
                                    DataSnapshot.new {
                                        this.device = user.device
                                        this.latitude = message.latitude
                                        this.longitude = message.longitude
                                        this.bearing = message.bearing.toDouble()
                                        this.bearingAccuracy = message.bearingAccuracy?.toDouble()
                                        this.locationAccuracy = message.locationAccuracy.toDouble()
                                        this.batteryLevel = message.batteryLevel
                                        this.batteryCharging = message.batteryCharging
                                        this.createdAt = Instant.fromEpochSeconds(message.time)
                                        this.device = user.device
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        get {
            val user = call.principal<TrailsAppUserPrincipal>()
            call.respond("Hi, $user")
        }
    }
}

@Serializable
private sealed class TrailsWebSocketAppMessage {
    @SerialName("data_snapshot")
    @Serializable
    data class DataSnapshot(
        @SerialName("latitude") val latitude: Double,
        @SerialName("longitude") val longitude: Double,
        @SerialName("bearing") val bearing: Float,
        @SerialName("bearing_accuracy") val bearingAccuracy: Float? = null,
        @SerialName("location_accuracy") val locationAccuracy: Float,
        @SerialName("battery_level") val batteryLevel: Float? = null,
        @SerialName("battery_charging") val batteryCharging: Boolean? = null,
        @SerialName("time") val time: Long,
    ) : TrailsWebSocketAppMessage()
}
