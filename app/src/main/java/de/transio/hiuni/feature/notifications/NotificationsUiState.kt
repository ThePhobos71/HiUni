package de.transio.hiuni.feature.notifications

import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationLogEntity

data class NotificationsUiState(
    /** Bereits nach [selectedCategory] gefilterte, anzuzeigende Liste. */
    val items: List<NotificationLogEntity> = emptyList(),
    val unreadCount: Int = 0,
    /** Pull-to-Refresh-Indicator-State. Lokales Push-Center kennt keinen Server-Sync,
     *  daher fungiert das Refresh nur als sichtbares Acknowledgement + Prune-Trigger. */
    val isRefreshing: Boolean = false,
    /**
     * Aktiver Kategorie-Filter. `null` = „Alle". Nur Kategorien, für die
     * überhaupt Einträge existieren, werden als Pill angeboten ([availableCategories]).
     */
    val selectedCategory: NotificationCategory? = null,
    /** Kategorien, die in der (ungefilterten) Liste vorkommen — Basis für die Filter-Pills. */
    val availableCategories: List<NotificationCategory> = emptyList()
)
