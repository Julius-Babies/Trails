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
        /**
         * Whether the channel plays its own notification sound. Set to `false`
         * for channels that produce sound by other means (e.g. the ring channel
         * plays a looping ringtone via RingService) so the OS doesn't double up.
         */
        playSound: Boolean = true,
    )

    /** Removes a previously created channel (no-op on platforms without channels). */
    fun deleteChannel(channelId: String)

    suspend fun sendNotification(
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
    ): Boolean

    companion object {
        const val PING_CHANNEL_ID = "ping_channel"

        /**
         * A channel's importance is fixed at creation — the OS ignores later
         * importance changes from code. The original ring channel was created
         * silent (IMPORTANCE_NONE), which prevents its full-screen intent from
         * launching. Bumping the id forces a fresh channel at the correct
         * importance on existing installs; [LEGACY_RING_CHANNEL_ID] is deleted.
         */
        const val RING_CHANNEL_ID = "ring_channel_v2"
        const val LEGACY_RING_CHANNEL_ID = "ring_channel"
    }

    enum class Importance {
        IMPORTANCE_UNSPECIFIED, IMPORTANCE_NONE,
        IMPORTANCE_MIN, IMPORTANCE_LOW, IMPORTANCE_DEFAULT, IMPORTANCE_HIGH
    }
}