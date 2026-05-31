package es.jvbabi.trails.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import es.jvbabi.trails.data.repository.NotificationRepository
import es.jvbabi.trails.shared.compose.R

class RingService: Service() {
    private var ringtone: Ringtone? = null
    private var causedByDeviceName: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRinging(intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "")
            ACTION_STOP  -> stopRinging()
        }
        return START_STICKY
    }

    private fun startRinging(deviceName: String) {
        causedByDeviceName = deviceName
        setVolume(20)
        playRingtone()
        startForeground(30, buildCallNotification())
        startActivity(
            Intent(ACTION_SHOW_RINGING).apply {
                `package` = packageName
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun stopRinging() {
        ringtone?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setVolume(percent: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        am.setStreamVolume(
            AudioManager.STREAM_RING,
            (max * percent.coerceIn(0, 100)) / 100,
            0
        )
    }

    private fun playRingtone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).also {
            it.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.isLooping = true
            }
            it.play()
        }
    }

    private fun buildCallNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            this, 0,
            Intent(ACTION_SHOW_RINGING).apply {
                `package` = packageName
                putExtra(EXTRA_DEVICE_NAME, causedByDeviceName)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationRepository.RING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Finding your device")
            .setContentText("Tap to stop ringing")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_START = "ACTION_START_RING"
        const val ACTION_STOP  = "ACTION_STOP_RING"
        const val ACTION_SHOW_RINGING = "es.jvbabi.trails.ACTION_SHOW_RINGING"
        const val EXTRA_DEVICE_NAME = "caused_by_device_name"
    }
}