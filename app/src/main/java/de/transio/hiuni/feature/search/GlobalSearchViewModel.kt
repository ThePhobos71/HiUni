package de.transio.hiuni.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.search.GlobalSearchRepository
import de.transio.hiuni.core.search.GlobalSearchResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Snapshot der Spotlight-Suche fürs UI. Die [results] werden bei blank-Query
 * bewusst leer gehalten — die UI rendert dann einen „Tippe um zu suchen"-
 * Hint statt aller Inhalte.
 *
 * [appliedQuery] hält den Query-String, der dem aktuellen [results]-Snapshot
 * zugrunde liegt — solange sich [query] (live) und [appliedQuery] (debounced)
 * unterscheiden, ist die Suche noch „am Laufen" und [isLoading] wird true.
 */
data class GlobalSearchUiState(
    val query: String = "",
    val appliedQuery: String = "",
    val results: GlobalSearchResults = GlobalSearchResults.Empty
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isLoading: Boolean
        get() = hasQuery && query.trim() != appliedQuery.trim()
}

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchRepository: GlobalSearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")

    // Debounce nur den Query-Stream. 200 ms ist „nach kurzer Tipp-Pause":
    // schnell genug für reaktives Feedback, langsam genug um nicht bei jedem
    // Keystroke 6 DB-Queries auszulösen.
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val debouncedQuery = _query
        .debounce(200)
        .distinctUntilChanged()

    /**
     * Bündelt den Query, der dem aktuellen Result-Snapshot zugrunde liegt, mit
     * den Ergebnissen selbst. Das UI vergleicht `liveQuery` aus [_query] mit
     * `resultQuery` aus diesem Tuple, um „loading" während des 200ms-Debounce-
     * Fensters anzuzeigen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val resultsFlow = debouncedQuery
        .flatMapLatest { q -> searchRepository.search(q).map { results -> q to results } }

    val state: StateFlow<GlobalSearchUiState> = combine(
        _query,
        resultsFlow
    ) { liveQuery, (resultQuery, results) ->
        GlobalSearchUiState(
            query = liveQuery,
            appliedQuery = resultQuery,
            results = results
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun setQuery(query: String) {
        _query.update { query }
    }

    fun clearQuery() {
        _query.update { "" }
    }
}
