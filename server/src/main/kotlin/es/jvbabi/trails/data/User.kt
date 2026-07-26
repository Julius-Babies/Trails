package es.jvbabi.trails.data

import es.jvbabi.trails.config.ApplicationConfig
import es.jvbabi.trails.database.DbUser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

interface User {
    val id: String
    val username: String
}

class UserId(from: String) {
    val host = from.substringBefore("/")
    val id = from.substringAfter("/").let(Uuid::parse)

    init {
        require(from.matches(Regex(".+/u/.+"))) { "Invalid user id format" }
    }
}

class LocalUser(
    private val dbUser: DbUser,
): User, KoinComponent {
    private val applicationConfig by inject<ApplicationConfig>()

    override val id: String = "${applicationConfig.url.host}/u/${dbUser.id.value.toHexString()}"
    override val username: String = dbUser.username
}

abstract class RemoteUser(
    homeserver: String,
    userId: String,
): User {
    override val id: String = "$homeserver/u/$userId"
}