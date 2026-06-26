package de.transio.hiuni.feature.notifications

import de.transio.hiuni.core.notifications.data.NotificationLogEntity

data class NotificationsUiState(
    val items: List<NotificationLogEntity> = emptyList(),
    val unreadCount: Int = 0,
    /** Pull-to-Refresh-Indicator-State. Lokales Push-Center kennt keinen Server-Sync,
     *  daher fungiert das Refresh nur als sichtbares Acknowledgement + Prune-Trigger. */
    val isRefreshing: Boolean = false
)
