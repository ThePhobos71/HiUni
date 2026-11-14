package de.transio.hiuni.feature.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.mensa.data.FavoriteMealNotifier
import de.transio.hiuni.feature.mensa.data.FavoriteMealStore
import de.transio.hiuni.feature.mensa.data.FavoriteMealWatcher
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.MensaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein gemerktes Stichwort mit seinem nächsten Treffer im Speiseplan (falls vorhanden). */
data class FavoriteMealRow(val keyword: String, val nextHit: MealEntity?)

data class FavoriteMealsUiState(
    val rows: List<FavoriteMealRow> = emptyList(),
    /** Das zuletzt eingegebene Stichwort wurde abgelehnt (zu kurz, doppelt, Liste voll). */
    val rejected: Boolean = false
)

/**
 * Liste "Lieblingsgerichte" in den Mensa-Einstellungen: Stichwörter hinzufügen und
 * entfernen. Der Alarm selbst läuft im [FavoriteMealWatcher].
 */
@HiltViewModel
class FavoriteMealsViewModel @Inject constructor(
    private val store: FavoriteMealStore,
    private val watcher: FavoriteMealWatcher,
    repository: MensaRepository
) : ViewModel() {

    private val rejected = MutableStateFlow(false)

    val state: StateFlow<FavoriteMealsUiState> = combine(
        store.observeKeywords(),
        repository.observeSearchWindow(),
        rejected
    ) { keywords, meals, isRejected ->
        val next = FavoriteMealNotifier.nextHits(keywords, meals)
        FavoriteMealsUiState(
            rows = keywords.map { FavoriteMealRow(it, next[it]) },
            rejected = isRejected
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoriteMealsUiState())

    fun add(raw: String) = viewModelScope.launch {
        val added = store.addKeyword(raw)
        rejected.value = !added
        // Treffer, die schon im Plan stehen, nicht nachmelden: man sieht sie in der Liste.
        if (added) watcher.check(notify = false)
    }

    fun remove(keyword: String) = viewModelScope.launch {
        store.removeKeyword(keyword)
    }

    /** Hinweis ausblenden, sobald wieder getippt wird. */
    fun clearRejected() {
        rejected.value = false
    }
}
