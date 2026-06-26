package de.transio.hiuni.feature.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExamsViewModel @Inject constructor(
    repository: LsfExamsRepository
) : ViewModel() {

    val state: StateFlow<ExamsUiState> = repository.observeAll()
        .map { exams ->
            // Defensiv: das DAO sortiert bereits, aber wir wollen pro UI-Refactor
            // garantieren, dass „mit Datum aufsteigend, dann ohne Datum ans Ende"
            // stabil bleibt — auch wenn jemand später das DAO-Query anpasst.
            val sorted = exams.sortedWith(
                compareBy(
                    { it.examDate == null },
                    { it.examDate }
                )
            )
            ExamsUiState(exams = sorted, isLoading = false)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExamsUiState(isLoading = true)
        )
}
