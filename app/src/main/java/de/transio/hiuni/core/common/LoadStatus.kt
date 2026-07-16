package de.transio.hiuni.core.common

/**
 * Einheitlicher Lade-/Fehler-Baustein für UiStates.
 *
 * ── Warum ein eingebettetes Value-Objekt und KEINE sealed class ──
 *
 * Die ~20 UiStates der App benannten ihren Lade-/Fehlerzustand vorher
 * uneinheitlich (`errorMessage` vs. `lastError` vs. `error`; mal `isLoading`,
 * mal nur `isRefreshing`, mal `syncing`). Das erschwerte generische
 * Loading/Error-Composables ([de.transio.hiuni.core.design.components.ErrorState],
 * [de.transio.hiuni.core.design.components.HiUniSkeleton]).
 *
 * Eine sealed `UiState<T>`-Hierarchie (Loading/Content/Error) wäre invasiv: die
 * meisten Screens zeigen *gleichzeitig* Stale-Content UND einen Refresh-Spinner
 * UND ggf. einen Fehler — das ist kein Entweder-oder. Ein sealed-Korsett würde
 * genau dieses „Content + Overlay"-Muster kaputtmachen. Deshalb: EIN kleines
 * Value-Objekt, das als Feld `load: LoadStatus` in den bestehenden data classes
 * lebt und die drei orthogonalen Achsen bündelt:
 *
 *  - [isLoading]    — Erst-Load ohne Cache (steuert den Skeleton-Platzhalter).
 *  - [isRefreshing] — Pull-to-Refresh über vorhandenem Cache (steuert den
 *                     PullToRefresh-Indicator).
 *  - [error]        — Fehlermeldung des letzten Refreshs (`null` = kein Fehler).
 *
 * Migrationsschmerz ist minimal, weil die UiStates weiterhin delegierende
 * Convenience-Accessoren (`val isLoading get() = load.isLoading`, `errorMessage`
 * usw.) anbieten — Screens und Tests, die `state.isLoading`/`state.errorMessage`
 * lesen, bleiben unverändert. Vereinheitlicht wird die *Quelle* (ein `load`-Feld
 * mit kanonischen Namen), nicht die Aufrufseite.
 *
 * Convenience-Fabriken: [Idle], [loading], [refreshing], [failed].
 */
data class LoadStatus(
    /** Erst-Load ohne Cache. `true` bis zur ersten Content-Emission. */
    val isLoading: Boolean = false,
    /** Pull-to-Refresh über vorhandenem Cache. */
    val isRefreshing: Boolean = false,
    /** Fehlermeldung des letzten Refreshs; `null` = kein Fehler. */
    val error: String? = null
) {
    /** Läuft gerade *irgendein* Ladevorgang (Erst-Load oder Refresh)? */
    val isBusy: Boolean get() = isLoading || isRefreshing

    /** Liegt ein Fehler vor? */
    val hasError: Boolean get() = error != null

    companion object {
        /** Ruhezustand: kein Laden, kein Fehler. */
        val Idle = LoadStatus()

        /** Erst-Load läuft (kein Cache). */
        fun loading(): LoadStatus = LoadStatus(isLoading = true)

        /** Pull-to-Refresh über vorhandenem Cache läuft. */
        fun refreshing(): LoadStatus = LoadStatus(isRefreshing = true)

        /** Refresh mit Fehler abgeschlossen. */
        fun failed(message: String?): LoadStatus = LoadStatus(error = message)
    }
}
