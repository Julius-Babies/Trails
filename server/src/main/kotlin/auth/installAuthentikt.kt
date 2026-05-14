package es.jvbabi.trails.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import es.jvbabi.authentikt.core.userselection.plugins.builtin.EmailUserSelectionPlugin
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.User
import es.jvbabi.trails.database.Users
import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.ktor.ext.inject

class TrailsAuthentiktUser(private val dbUser: User): AuthentiktUser<User>(dbUser) {
    override suspend fun getEmail(): String = user.email
    override suspend fun getUsername(): String = user.username
    override suspend fun getDisplayName(): String = user.username
}

fun Application.installAuthentikt() {

    val db by inject<DatabaseManager>()

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

        val passwordPlugin = PasswordPlugin<TrailsAuthentiktUser> {
            checkPassword { user, password ->
                return@checkPassword db.transaction { user.user.password } == BCrypt.withDefaults().hashToString(12, password.toCharArray())
            }
        }
        install(passwordPlugin)

        val totpPlugin = TotpPlugin<TrailsAuthentiktUser> {
            getSecret { user -> db.transaction { user.user.otp!! } }
        }
        install(totpPlugin)

        val donePlugin = DonePlugin<TrailsAuthentiktUser> {
            generateToken { session, user ->
                return@generateToken "test-token-for-${user.getUsername()}"
            }
        }
        install(donePlugin)

        authorization { session, user ->
            when {
                !session.has(passwordPlugin) -> passwordPlugin
                db.transaction { user.user.otp } != null && !session.has(totpPlugin) -> totpPlugin
                else -> donePlugin
            }
        }
    }

    loadKoinModules(module { single { instance } })
}