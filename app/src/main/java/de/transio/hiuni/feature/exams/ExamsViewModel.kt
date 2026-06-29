package de.transio.hiuni.feature.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExamsViewModel @Inject constructor(
    repository: LsfExamsRepository,
    private val lsfSyncScheduler: LsfSyncScheduler
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val state: StateFlow<ExamsUiState> = combine(
        repository.observeAll(),
        _isRefreshing
    ) { exams, refreshing ->
        // Defensiv: das DAO sortiert bereits, aber wir wollen pro UI-Refactor
        // garantieren, dass „mit Datum aufsteigend, dann ohne Datum ans Ende"
        // stabil bleibt — auch wenn jemand später das DAO-Query anpasst.
        val sorted = exams.sortedWith(
            compareBy(
                { it.examDate == null },
                { it.examDate }
            )
        )
        ExamsUiState(exams = sorted, isLoading = false, isRefreshing = refreshing)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(60_000),
        ExamsUiState(isLoading = true)
    )

    /**
     * Pull-to-Refresh / Empty-State-Button: triggert den LSF-Sync (der die
     * Klausur-Refresh in seiner Phase mitnimmt). Worker liefert kein synchrones
     * Completion-Signal — Indicator bleibt 3 s sichtbar, danach kommt der
     * eigentliche Datenfluss über `observeAll()` automatisch nach.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            lsfSyncScheduler.triggerNow()
            delay(3000L)
            _isRefreshing.value = false
        }
    }
}
