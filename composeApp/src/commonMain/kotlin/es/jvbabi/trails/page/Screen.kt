package es.jvbabi.trails.page

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {

    @Serializable
    data object Home: Screen()

    @Serializable
    data object Settings: Screen()
}