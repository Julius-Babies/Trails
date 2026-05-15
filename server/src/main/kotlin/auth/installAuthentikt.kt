package es.jvbabi.trails.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import es.jvbabi.authentikt.core.userselection.plugins.builtin.EmailUserSelectionPlugin
import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.User
import es.jvbabi.trails.database.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import kotlin.text.toCharArray
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

        val emailPlugin = EmailUserSelectionPlugin {
            findUserByEmail { email ->
                val user = db.transaction { User.find { (Users.email eq email) or (Users.username eq email) }.firstOrNull() }
                user?.let { TrailsAuthentiktUser(it) }
            }

            withUsername = true
        }
        install(emailPlugin)

        val passwordPlugin = PasswordPlugin<User> {
            checkPassword { user, password ->
                return@checkPassword BCrypt.verifyer().verify(password.toCharArray(), db.transaction { user.password }).verified
            }
        }
        install(passwordPlugin)

        val totpPlugin = TotpPlugin<User> {
            getSecret { user -> db.transaction { user.otp!! } }
        }
        install(totpPlugin)

        val donePlugin = DonePlugin<User> {
            onSuccess { session, user ->
                val url = URLBuilder().apply {
                    protocol = URLProtocol("trailsapp", -1)
                    host = "application"
                    appendPathSegments(applicationConfig.url.host)
                    appendPathSegments("auth", "redirect")
                    parameters.append("token", "test-token-for-${user.id}")
                }
                redirect(url.buildString())
            }
        }
        install(donePlugin)

        install(deviceSelectionAuthentiktPlugin)

        authorization { session, user ->
            when {
                !session.has(passwordPlugin) -> passwordPlugin
                db.transaction { user.user.otp } != null && !session.has(totpPlugin) -> totpPlugin
                else -> {
                    val nextStep = authSessionDeviceSelection(session, user.user)
                    if (nextStep != null) return@authorization nextStep

                    return@authorization donePlugin
                }
            }
        }
    }

    loadKoinModules(module { single { instance } })
}