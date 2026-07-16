package de.transio.hiuni.core.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Dünner Adapter, damit der Compose-Baum (AdaptiveScaffold) den prozessweiten
 * [ConnectivityObserver.isOnline]-Zustand via `hiltViewModel()` beziehen kann,
 * ohne den Observer direkt in die UI zu injizieren.
 *
 * Der Observer-Flow ist bereits ein prozessweiter StateFlow; wir re-`stateIn`en
 * ihn nur auf den ViewModel-Scope, damit `collectAsStateWithLifecycle` einen
 * lifecycle-gebundenen Sammel-Punkt bekommt.
 */
@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    observer: ConnectivityObserver,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = observer.isOnline.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        observer.isOnline.value,
    )
}
