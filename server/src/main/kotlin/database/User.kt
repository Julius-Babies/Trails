package es.jvbabi.trails.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class User(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<User>(Users)

    var username by Users.username
    var email by Users.email
    var password by Users.password
    var otp by Users.otp
}

object Users : UuidTable("users") {
    val username = varchar("username", 255)
    val email = varchar("email", 255)
    val password = varchar("password", 255)
    val otp = varchar("otp", 255).nullable().default(null)
}