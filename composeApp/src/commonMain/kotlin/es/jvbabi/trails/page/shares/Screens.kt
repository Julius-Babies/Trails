package es.jvbabi.trails.page.shares

import kotlinx.serialization.Serializable

@Serializable
sealed class SharesScreen {
    @Serializable object Main : SharesScreen()
    @Serializable object NewShare : SharesScreen()
}