package es.jvbabi.trails.domain.usecase

import es.jvbabi.trails.data.repository.NotificationRepository
import kotlinx.coroutines.flow.first

class SetupNotificationsUseCase(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke() {
        val hasNotificationPermissions = notificationRepository.hasPermission().first()
        if (!hasNotificationPermissions) return

        notificationRepository.createChannel(
            channelId = NotificationRepository.PING_CHANNEL_ID,
            channelName = "Ping",
            description = "Wird verwendet, um dein Gerät zu finden",
            importance = NotificationRepository.Importance.IMPORTANCE_HIGH,
            bypassDnd = true,
        )

        notificationRepository.createChannel(
            channelId = NotificationRepository.RING_CHANNEL_ID,
            channelName = "Anklingeln",
            description = "Lasse dein Gerät klingeln, um es zu finden",
            importance = NotificationRepository.Importance.IMPORTANCE_NONE,
            bypassDnd = true,
        )
    }
}