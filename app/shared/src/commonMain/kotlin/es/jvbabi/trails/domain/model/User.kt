package es.jvbabi.trails.domain.model

import kotlin.uuid.Uuid

data class User(
    val id: Uuid,
    val homeserver: String,
    val username: String,
)