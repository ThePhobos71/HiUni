package de.transio.hiuni.feature.widgets.common

/**
 * Glance rendert Widgets als `RemoteViews`. Die generierten Container-Layouts
 * (Row/Column/Box) haben eine **harte Obergrenze von 10 direkten Kindern**:
 * `LayoutSelection.insertContainerView` kappt bei >10 stillschweigend auf 10
 * und loggt einen `IllegalArgumentException`-Stacktrace ("… container cannot
 * have more than 10 elements"). Das 11. Kind fehlt dann in der Anzeige.
 *
 * Diese Konstante macht die Grenze explizit, damit dynamisch aufgebaute
 * Container (gemappte Listen, konditionale Chip-Reihen) sie nicht "magisch"
 * überschreiten. Jeder Spacer, jedes Icon und jeder Text zählt als eigenes
 * Kind — verschachtelte Composables (z.B. eine Chip-Funktion, die Image +
 * Spacer + Text direkt emittiert) zählen mit ihrer *entpackten* Kinderzahl.
 */
const val GLANCE_MAX_CONTAINER_CHILDREN = 10

/**
 * Kappt [items] so, dass zusammen mit [fixedSiblings] fest vorhandenen
 * Geschwister-Kindern die [GLANCE_MAX_CONTAINER_CHILDREN]-Grenze eingehalten
 * wird. Wird ein zusätzliches Overflow-Element (z.B. eine "+N"-Zeile) als
 * letztes Kind angehängt, muss dieses über [reserveForOverflow] mitgezählt
 * werden, damit es nicht selbst das 11. Kind wird.
 *
 * Reine Funktion ohne Glance-Abhängigkeit — bewusst unit-testbar gehalten.
 *
 * @param fixedSiblings Anzahl fester (nicht aus der Liste stammender) Kinder
 *   im selben Container.
 * @param reserveForOverflow true, wenn nach der gekappten Liste noch ein
 *   Overflow-Hinweis als weiteres Kind folgt.
 * @return maximale Anzahl Listen-Einträge, die sicher gerendert werden können.
 */
fun capForContainer(
    fixedSiblings: Int = 0,
    reserveForOverflow: Boolean = false,
): Int {
    val overflowSlot = if (reserveForOverflow) 1 else 0
    return (GLANCE_MAX_CONTAINER_CHILDREN - fixedSiblings - overflowSlot).coerceAtLeast(0)
}
