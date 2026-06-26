package de.transio.hiuni.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationLogRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val state: StateFlow<NotificationsUiState> = combine(
        repository.observeRecent(),
        repository.observeUnreadCount(),
        _isRefreshing
    ) { items, unread, refreshing ->
        NotificationsUiState(items = items, unreadCount = unread, isRefreshing = refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        // Beim Aufruf des Push-Centers ältere Einträge wegräumen — der User
        // sieht ohnehin nur frische Mitteilungen, Historie wandert sonst ins
        // Nichts und treibt die DB-Größe in die Höhe.
        viewModelScope.launch {
            runCatching {
                repository.prune(olderThan = Instant.now().minus(30, ChronoUnit.DAYS))
            }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch { repository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /**
     * Pull-to-Refresh: nichts Server-seitiges zu syncen — der Center ist lokal.
     * Wir nutzen die Geste als sichtbares "frische Sicht"-Ack + Trigger fürs
     * Prune. ~600ms Indicator-Dauer gibt dem User Feedback ohne ihn warten zu
     * lassen.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                repository.prune(olderThan = Instant.now().minus(30, ChronoUnit.DAYS))
            }
            delay(600L)
            _isRefreshing.value = false
        }
    }
}
