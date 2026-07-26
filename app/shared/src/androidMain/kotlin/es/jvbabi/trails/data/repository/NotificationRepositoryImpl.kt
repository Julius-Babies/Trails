package es.jvbabi.trails.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import es.jvbabi.trails.shared.compose.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class NotificationRepositoryImpl(
    private val context: Context,
): NotificationRepository {

    override fun hasPermission(): Flow<Boolean> {
        return flow {
            while (currentCoroutineContext().isActive) {
                val hasPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
                emit(hasPermission)
            }
        }.distinctUntilChanged()
    }

    override fun createChannel(
        channelId: String,
        channelName: String,
        description: String,
        importance: NotificationRepository.Importance,
        bypassDnd: Boolean,
        playSound: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (importance) {
                NotificationRepository.Importance.IMPORTANCE_UNSPECIFIED -> NotificationManager.IMPORTANCE_UNSPECIFIED
                NotificationRepository.Importance.IMPORTANCE_LOW -> NotificationManager.IMPORTANCE_LOW
                NotificationRepository.Importance.IMPORTANCE_MIN -> NotificationManager.IMPORTANCE_MIN
                NotificationRepository.Importance.IMPORTANCE_HIGH -> NotificationManager.IMPORTANCE_HIGH
                NotificationRepository.Importance.IMPORTANCE_NONE -> NotificationManager.IMPORTANCE_NONE
                NotificationRepository.Importance.IMPORTANCE_DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
            }

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                this@apply.description = description
                enableLights(true)
                setBypassDnd(bypassDnd)
                if (!playSound) {
                    // The ring channel must be high-importance (so its full-screen
                    // intent can launch), but its audio is the looping ringtone from
                    // RingService — so silence the channel's own notification sound.
                    setSound(null, null)
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun deleteChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannel(channelId)
        }
    }

    override suspend fun sendNotification(
        channelId: String,
        title: String,
        body: String,
        notificationId: Int
    ): Boolean {
        if (!hasPermission().first()) return false
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.icon_mono_large)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
        return true
    }
}