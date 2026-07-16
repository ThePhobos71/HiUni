package de.transio.hiuni.feature.lsf.data

/**
 * Reine, Android-/Netz-freie Entscheidungslogik für die „Neuer Kurs im LSF"-
 * Pushes — bewusst aus [LsfMyCoursesRepositoryImpl] herausgezogen, damit sie als
 * JVM-Unit-Test ohne HTTP-/Room-/Firebase-Runtime läuft (gleiches Muster wie
 * [de.transio.hiuni.core.push.MailTickleHandler]).
 *
 * Regeln:
 *  - Erst-Sync (`isFirstSync`) → GAR KEINE Meldung, nur Bestandsimport. Sonst
 *    würde der User beim ersten Login/Semesterwechsel mit einer Flut von
 *    „Neuer Kurs"-Pushes für seinen gesamten Stundenplan zugespammt.
 *  - Keine neuen Kurse → nichts.
 *  - > [BULK_THRESHOLD] neue Kurse auf einmal → EINE Sammel-Meldung
 *    (Re-Login/Semesterwechsel).
 *  - sonst je neuem Kurs eine Einzel-Meldung, dedupliziert per RefKey `course:<lsfId>`.
 */
object CourseDiffNotifier {

    /**
     * Ab wie vielen neuen Kursen auf einmal statt N Einzel-Pushes EINE
     * Sammel-Meldung geschickt wird.
     */
    const val BULK_THRESHOLD = 10

    /** Eine zu erzeugende Push-Meldung (schon fertig aufbereitet). */
    data class Push(val title: String, val body: String, val refKey: String)

    /**
     * Ergebnis der Diff-Entscheidung: die Liste der zu feuernden Pushes (leer =
     * nichts tun).
     *
     * @param isFirstSync  ob noch nie ein vollständiger LSF-Sync lief.
     * @param newCourses   neu hinzugekommene Kurse als (lsfId, titel).
     */
    fun decide(isFirstSync: Boolean, newCourses: List<Pair<String, String>>): List<Push> {
        if (isFirstSync || newCourses.isEmpty()) return emptyList()
        if (newCourses.size > BULK_THRESHOLD) {
            val refKey = "courses:bulk:${newCourses.size}:" +
                newCourses.joinToString(",") { it.first }.hashCode()
            return listOf(
                Push(
                    title = "Neue Kurse im LSF",
                    body = "${newCourses.size} neue Kurse wurden hinzugefügt.",
                    refKey = refKey
                )
            )
        }
        return newCourses.map { (lsfId, titel) ->
            Push(title = "Neuer Kurs im LSF", body = titel, refKey = "course:$lsfId")
        }
    }
}
