package de.transio.hiuni.feature.notifications

import de.transio.hiuni.core.notifications.data.NotificationLogEntity

data class NotificationsUiState(
    val items: List<NotificationLogEntity> = emptyList(),
    val unreadCount: Int = 0
)
