package de.transio.hiuni.feature.grades.data

import de.transio.hiuni.core.notifications.data.NotificationKind

/**
 * Reine, Android-/Netz-freie Entscheidungslogik für die „Neue Note eingetragen"-
 * Pushes. Bewusst aus [GradesRepositoryImpl] herausgezogen, damit sie als
 * JVM-Unit-Test ohne HTTP-/Room-/Firebase-Runtime läuft (gleiches Muster wie
 * [de.transio.hiuni.feature.lsf.data.CourseDiffNotifier]).
 *
 * Regeln:
 *  - Leere Baseline (noch keine Leistung in der DB: erster Login, Re-Login nach
 *    Logout/Reset) → GAR KEINE Meldung, nur Bestandsimport. Sonst bekäme der
 *    User beim ersten Sync für jede schon vorhandene Note einen Push.
 *  - Als „neu benotet" zählt:
 *     - eine neue Zeile, die bereits eine Note trägt bzw. PASSED/FAILED ist
 *       (frisch angemeldete Prüfungen ohne Ergebnis zählen NICHT),
 *     - eine bestehende Zeile, deren Status von REGISTERED auf PASSED/FAILED
 *       wechselt oder deren Note von „keine" auf „vorhanden" wechselt.
 *    Eine Notenkorrektur (2,7 → 2,3) löst bewusst keinen Push aus.
 *  - Bis [BULK_THRESHOLD] neu benotete Leistungen → je Leistung eine Einzel-
 *    Meldung (der Titel sagt, WELCHE Prüfung raus ist, das will man wissen).
 *  - Mehr auf einmal (z.B. nach längerem Offline-Fenster oder wenn das
 *    Prüfungsamt einen ganzen Schwung einträgt) → EINE Sammel-Meldung, damit
 *    der Sperrbildschirm nicht mit Pushes zugeschüttet wird.
 *
 * DATENSCHUTZ: Die Note selbst steht NIE im Text (Sperrbildschirm!), nur der Titel.
 */
object GradeDiffNotifier {

    /** Bis zu so vielen neuen Noten auf einmal gibt es Einzel-Pushes, darüber eine Sammel-Meldung. */
    const val BULK_THRESHOLD = 3

    /** Wie viele Titel die Sammel-Meldung namentlich nennt, bevor „und N weitere" folgt. */
    const val BULK_TITLES_SHOWN = 3

    /** [NotificationKind] für alle Noten-Pushes (Kategorie „Noten", eigener Android-Channel). */
    val KIND: NotificationKind = NotificationKind.GRADE

    /** Der für den Diff relevante Ausschnitt einer Leistungszeile (DB-Stand oder frisch geparsed). */
    data class GradeSnapshot(
        val mergeKey: String,
        val labnr: Long?,
        val titel: String,
        val note: Double?,
        val status: GradeStatus
    )

    /** Eine zu erzeugende Push-Meldung (schon fertig aufbereitet), inkl. Kategorie-Kind. */
    data class Push(
        val title: String,
        val body: String,
        val refKey: String,
        val kind: NotificationKind = KIND
    )

    /**
     * Ergebnis der Diff-Entscheidung: die Liste der zu feuernden Pushes (leer =
     * nichts tun).
     *
     * @param previous DB-Stand VOR dem Sync (Baseline). Leer = Erst-Sync.
     * @param current  frisch geparster Notenspiegel.
     */
    fun decide(previous: Collection<GradeSnapshot>, current: List<GradeSnapshot>): List<Push> {
        if (previous.isEmpty()) return emptyList()
        val previousByKey = previous.associateBy { it.mergeKey }
        val newlyGraded = current.filter { now ->
            val before = previousByKey[now.mergeKey]
            if (before == null) isGraded(now) else becameGraded(before, now)
        }
        if (newlyGraded.isEmpty()) return emptyList()
        if (newlyGraded.size > BULK_THRESHOLD) return listOf(bulkPush(newlyGraded))
        return newlyGraded.map { grade ->
            Push(title = "Neue Note eingetragen", body = grade.titel, refKey = refKeyFor(grade))
        }
    }

    /** Zeile gilt als benotet, wenn sie eine Note trägt ODER bereits PASSED/FAILED ist. */
    internal fun isGraded(g: GradeSnapshot): Boolean =
        g.note != null || g.status == GradeStatus.PASSED || g.status == GradeStatus.FAILED

    /**
     * Bestehende Zeile hat gerade eine Note/ein Endergebnis bekommen:
     *  - Status wechselte von REGISTERED auf PASSED/FAILED, ODER
     *  - vorher keine Note, jetzt eine Note.
     */
    internal fun becameGraded(before: GradeSnapshot, now: GradeSnapshot): Boolean {
        val statusUnlocked = before.status == GradeStatus.REGISTERED &&
            (now.status == GradeStatus.PASSED || now.status == GradeStatus.FAILED)
        val noteAppeared = before.note == null && now.note != null
        return statusUnlocked || noteAppeared
    }

    /** RefKey je Leistung: labnr bevorzugt (stabil), sonst Titel-Fallback. */
    private fun refKeyFor(g: GradeSnapshot): String =
        if (g.labnr != null) "grade:${g.labnr}" else "grade:t:${g.titel}"

    private fun bulkPush(grades: List<GradeSnapshot>): Push {
        val shown = grades.take(BULK_TITLES_SHOWN).joinToString(", ") { it.titel }
        val rest = grades.size - BULK_TITLES_SHOWN
        val body = if (rest > 0) "$shown und $rest weitere" else shown
        val refKey = "grades:bulk:${grades.size}:" +
            grades.joinToString(",") { it.mergeKey }.hashCode()
        return Push(title = "${grades.size} neue Noten eingetragen", body = body, refKey = refKey)
    }
}

/** Diff-Ausschnitt einer persistierten Leistung. */
fun GradeEntity.toSnapshot(): GradeDiffNotifier.GradeSnapshot =
    GradeDiffNotifier.GradeSnapshot(mergeKey, labnr, titel, note, status)

/** Diff-Ausschnitt einer frisch geparsten Leistung. */
fun ParsedGrade.toSnapshot(): GradeDiffNotifier.GradeSnapshot =
    GradeDiffNotifier.GradeSnapshot(mergeKey, labnr, titel, note, status)
