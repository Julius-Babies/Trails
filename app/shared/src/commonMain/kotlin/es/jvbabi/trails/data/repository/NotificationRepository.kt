package es.jvbabi.trails.data.repository

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun hasPermission(): Flow<Boolean>
    fun createChannel(
        channelId: String,
        channelName: String,
        description: String,
        importance: Importance,
        bypassDnd: Boolean,
    )

    suspend fun sendNotification(
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
    ): Boolean

    companion object {
        const val PING_CHANNEL_ID = "ping_channel"
    }

    enum class Importance {
        IMPORTANCE_UNSPECIFIED, IMPORTANCE_NONE,
        IMPORTANCE_MIN, IMPORTANCE_LOW, IMPORTANCE_DEFAULT, IMPORTANCE_HIGH
    }
}