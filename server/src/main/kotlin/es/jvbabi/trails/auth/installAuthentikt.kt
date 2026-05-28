package es.jvbabi.trails.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.step.plugins.builtin.*
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.*
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import util.date.plus
import java.security.MessageDigest
import java.time.ZoneOffset
import kotlin.text.toCharArray
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

val deviceModelAttribute = AttributeKey<String>("device_model")
val deviceManufacturerAttribute = AttributeKey<String>("device_manufacturer")
val authSessionSelectedDeviceIdAttribute = AttributeKey<Uuid>("auth_session_selected_device_id")

class TrailsAuthentiktUser(dbUser: User): AuthentiktUser<User>(dbUser) {
    override suspend fun getEmail(): String = user.email
    override suspend fun getUsername(): String = user.username
    override suspend fun getDisplayName(): String = user.username
}

fun Application.installAuthentikt() {

    val db by inject<DatabaseManager>()
    val applicationConfig by inject<ApplicationConfig>()
    val deviceSelectionAuthentiktPlugin = DeviceSelectionAuthentiktPlugin()
    loadKoinModules(module { single { deviceSelectionAuthentiktPlugin } })

    val instance = installAuthentikt {
        apiPrefix = "/api/v1/auth"

        var emailPlugin: EmailUserSelectionPlugin<User>? = null
        var passwordPlugin: PasswordPlugin<User>? = null
        var totpPlugin: TotpPlugin<User>? = null
        var oauthPlugin: OIDCPlugin<User>? = null

        if (applicationConfig.config.auth?.oauth == null) {
            emailPlugin = EmailUserSelectionPlugin {
                findUserByEmail { email ->
                    val user = db.transaction { User.find { (Users.email eq email) or (Users.username eq email) }.firstOrNull() }
                    user?.let { TrailsAuthentiktUser(it) }
                }

                withUsername = true
            }
            install(emailPlugin)

            passwordPlugin = PasswordPlugin {
                checkPassword { user, password ->
                    return@checkPassword BCrypt.verifyer().verify(password.toCharArray(), db.transaction { user.password }).verified
                }
            }
            install(passwordPlugin)

            totpPlugin = TotpPlugin {
                getSecret { user -> db.transaction { user.otp!! } }
            }
            install(totpPlugin)
        } else {
            oauthPlugin = OIDCPlugin {
                clientId = applicationConfig.config.auth!!.oauth!!.clientId
                clientSecret = applicationConfig.config.auth!!.oauth!!.clientSecret
                baseUrl = applicationConfig.config.baseUrl
                authorizationEndpoint = applicationConfig.config.auth!!.oauth!!.authorizationEndpoint
                userInfoEndpoint = applicationConfig.config.auth!!.oauth!!.userinfoEndpoint
                tokenEndpoint = applicationConfig.config.auth!!.oauth!!.tokenEndpoint
                uiLoginBaseUrl = URLBuilder(applicationConfig.config.baseUrl).apply {
                    appendPathSegments("auth", "authorize")
                }.buildString()
                scopes(*applicationConfig.config.auth!!.oauth!!.scopes.toTypedArray())

                onUserInfo { response ->
                    val map = response.body<Map<String, String>>()
                    val user = db.transaction { User.find { Users.email eq map["email"]!! }.firstOrNull() }
                    if (user == null) return@onUserInfo UserInfo.Result.Failure("user not found")
                    return@onUserInfo UserInfo.Result.Success(TrailsAuthentiktUser(user))
                }
            }
            install(oauthPlugin)
        }

        val donePlugin = DonePlugin<User> {
            onSuccess { session, user ->

                val deviceId = session.attributes[authSessionSelectedDeviceIdAttribute]
                val device = db.transaction { Device.findById(deviceId!!)!! }
                require(db.transaction { device.owner.id == user.id }) { "Device does not belong to user" }

                val jwt = JWT
                    .create()
                    .withAudience("trails-app")
                    .withIssuer("trails-app-server")
                    .withClaim("user_id", user.id.value.toString())
                    .withClaim("device_id", device.id.value.toString())
                    .withExpiresAt(
                        Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .plus(365.days)
                            .toJavaLocalDateTime()
                            .toInstant(ZoneOffset.UTC)
                    )
                    .sign(Algorithm.HMAC256(applicationConfig.jwtSecret))

                db.transaction {
                    Session.new {
                        this.device = device
                        this.tokenHash = MessageDigest.getInstance("SHA-256").digest(jwt.toByteArray()).joinToString("") { "%02x".format(it) }
                    }
                }

                val url = URLBuilder().apply {
                    protocol = URLProtocol("trailsapp", -1)
                    host = "application"
                    appendPathSegments(applicationConfig.url.host)
                    appendPathSegments("auth", "redirect")
                    parameters.append("token", jwt)
                }
                redirect(url.buildString())
            }
        }
        install(donePlugin)

        install(deviceSelectionAuthentiktPlugin)

        authorization { session ->
            val user = session.identifiedUser

            if (applicationConfig.config.auth?.oauth == null) {
                when {
                    user == null -> return@authorization emailPlugin!!
                    !session.has(passwordPlugin!!) -> return@authorization passwordPlugin
                    db.transaction { user.user.otp } != null && !session.has(totpPlugin!!) -> return@authorization totpPlugin
                }
            } else {
                if (user == null) return@authorization oauthPlugin!!
            }

            val nextStep = authSessionDeviceSelection(session, user.user)
            if (nextStep != null) return@authorization nextStep

            return@authorization donePlugin
        }
    }

    loadKoinModules(module { single { instance } })
}