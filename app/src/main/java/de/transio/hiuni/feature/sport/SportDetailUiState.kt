package de.transio.hiuni.feature.sport

import de.transio.hiuni.feature.sport.data.SportEventEntity

/**
 * State des Sport-Detail-Screens. `event = null` heißt "noch nicht geladen" oder
 * "Slot existiert nicht mehr im aktuellen Plan-Fenster" — der Screen rendert in
 * dem Fall den Empty-Detail-Fallback.
 *
 * [isCalendarPinned] spiegelt, ob ein `CustomEventEntity` mit `sourceReference =
 * "sport:$slotId"` und `sourceKind = SOURCE_SPORT_PIN` existiert. Treibt das
 * Toggle des "In Kalender"-Buttons.
 *
 * [pinMessage] ist die Einmal-Botschaft für die Snackbar nach Pin/Unpin.
 */
data class SportDetailUiState(
    val event: SportEventEntity? = null,
    val isCalendarPinned: Boolean = false,
    val pinMessage: String? = null
)
