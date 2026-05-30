package es.jvbabi.trails.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.Session
import es.jvbabi.trails.database.Sessions
import es.jvbabi.trails.database.User
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import kotlin.uuid.Uuid

const val TRAILS_USER_REALM = "trails"

fun Application.installAuthentication() {
    val applicationConfig by inject<ApplicationConfig>()
    val db by inject<DatabaseManager>()

    install(Authentication) {
        jwt(name = TRAILS_USER_REALM) {
            realm = "Trails API"
            verifier(JWT
                .require(Algorithm.HMAC256(applicationConfig.jwtSecret))
                .withAudience("trails-app")
                .withIssuer("trails-app-server")
                .build())

            validate { credential ->
                val originalJwt = (this.request.parseAuthorizationHeader() as HttpAuthHeader.Single).blob
                val jwtHash = MessageDigest.getInstance("SHA-256").digest(originalJwt.toByteArray()).joinToString("") { "%02x".format(it) }
                val userId = Uuid.parse(credential.payload.getClaim("user_id").asString())
                val session = db.transaction {
                    Sessions
                        .leftJoin(Devices, { Sessions.device }, { Devices.id })
                        .selectAll()
                        .where { Sessions.tokenHash eq jwtHash }
                        .andWhere { Devices.owner eq userId }
                        .andWhere { Sessions.invalidatedAt.isNull() }
                        .singleOrNull()
                        ?.let { Session.wrapRow(it) }
                }
                return@validate if (session == null) null
                else db.transaction {
                    val device = session.device
                    val user = device.owner
                    TrailsAppUserPrincipal(
                        user,
                        device,
                        session
                    )
                }
            }
        }
    }
}

data class TrailsAppUserPrincipal(
    val user: User,
    val device: Device,
    val session: Session,
): KoinComponent {
    private val db by inject<DatabaseManager>()

    suspend fun requireValidSession() {
        if (db.transaction { device.deletion } != null) throw RuntimeException("Device is deleted")
    }
}