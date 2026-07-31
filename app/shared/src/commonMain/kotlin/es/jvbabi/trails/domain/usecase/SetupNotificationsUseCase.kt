package es.jvbabi.trails.domain.usecase

import es.jvbabi.trails.data.repository.NotificationRepository
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import trails.app.shared.generated.resources.*

class SetupNotificationsUseCase(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke() {
        val hasNotificationPermissions = notificationRepository.hasPermission().first()
        if (!hasNotificationPermissions) return

        notificationRepository.createChannel(
            channelId = NotificationRepository.PING_CHANNEL_ID,
            channelName = getString(Res.string.notification_channel_ping_name),
            description = getString(Res.string.notification_channel_ping_description),
            importance = NotificationRepository.Importance.IMPORTANCE_HIGH,
            bypassDnd = true,
        )

        // Drop the legacy silent ring channel; its importance can't be raised in
        // place, so a fresh channel id is used (see RING_CHANNEL_ID).
        notificationRepository.deleteChannel(NotificationRepository.LEGACY_RING_CHANNEL_ID)

        notificationRepository.createChannel(
            channelId = NotificationRepository.RING_CHANNEL_ID,
            channelName = getString(Res.string.notification_channel_ring_name),
            description = getString(Res.string.notification_channel_ring_description),
            // High importance so the ring's full-screen intent can launch the
            // RingingActivity even from the background / on the lock screen.
            importance = NotificationRepository.Importance.IMPORTANCE_HIGH,
            bypassDnd = true,
            playSound = false,
        )
    }
}