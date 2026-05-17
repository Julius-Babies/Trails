package es.jvbabi.trails.domain.model

import kotlin.uuid.Uuid

data class ActiveShare(
    val id: Uuid,
    val device: Device,
)