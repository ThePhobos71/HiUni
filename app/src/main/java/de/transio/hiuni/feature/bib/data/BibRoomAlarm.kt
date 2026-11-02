package de.transio.hiuni.feature.bib.data

import java.time.LocalDate
import java.time.LocalTime

/** Modus des Raum-Alarms (Frei-Meldungen für Lieblings-Räume). */
enum class BibAlarmMode {
    /** Keine Frei-Meldungen; die Baseline läuft trotzdem still mit. */
    OFF,

    /** Nur am Aktivierungstag, ab dem Folgetag wie [OFF]. */
    TODAY_ONLY,

    /** Jeden Tag. */
    ALWAYS
}

/**
 * Einstellung des Raum-Alarms. Rein und Android-frei: der Notifier, die TTL-Wahl
 * im Prefetch und der Screen bekommen sie als Wert übergeben.
 *
 * „Nur heute" braucht keinen Mitternachts-Job: [activatedOn] hält den Tag der
 * Aktivierung fest, [effectiveMode] wertet das beim Lesen aus.
 *
 * @param durationMinutes gewünschte Mindestdauer eines gemeldeten Blocks
 *   (siehe [DURATION_OPTIONS]); zugleich die Länge der „Buchen"-Aktion.
 * @param fromHour Beginn des Zeitfensters (volle Stunde, inklusive).
 * @param toHour Ende des Zeitfensters (volle Stunde, exklusive).
 */
data class BibRoomAlarm(
    val mode: BibAlarmMode = BibAlarmMode.OFF,
    val activatedOn: LocalDate? = null,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val fromHour: Int = DEFAULT_FROM_HOUR,
    val toHour: Int = DEFAULT_TO_HOUR
) {

    /** Tatsächlich geltender Modus an [today]: abgelaufenes „Nur heute" ist [BibAlarmMode.OFF]. */
    fun effectiveMode(today: LocalDate): BibAlarmMode =
        if (mode == BibAlarmMode.TODAY_ONLY && activatedOn != today) BibAlarmMode.OFF else mode

    fun isActive(today: LocalDate): Boolean = effectiveMode(today) != BibAlarmMode.OFF

    val windowFrom: LocalTime get() = LocalTime.of(fromHour.coerceIn(0, 23), 0)

    /** Exklusives Fensterende; 24 Uhr wird als [LocalTime.MAX] abgebildet. */
    val windowUntil: LocalTime get() = if (toHour >= 24) LocalTime.MAX else LocalTime.of(toHour.coerceAtLeast(0), 0)

    /** `true`, wenn [time] im Fenster `[fromHour, toHour)` liegt. */
    fun inWindow(time: LocalTime): Boolean = !time.isBefore(windowFrom) && time.isBefore(windowUntil)

    /** `true`, wenn der Slot `[start, end)` vollständig im Fenster liegt. */
    fun containsSlot(start: LocalTime, end: LocalTime): Boolean =
        !start.isBefore(windowFrom) && (toHour >= 24 || !end.isAfter(windowUntil))

    /**
     * Neues Fenster-Von. Liegt es nicht mehr vor dem Bis, rutscht das Bis eine
     * Stunde dahinter, damit das Fenster nie leer wird.
     */
    fun withFromHour(hour: Int): BibRoomAlarm {
        val from = hour.coerceIn(MIN_HOUR, MAX_HOUR - 1)
        return copy(fromHour = from, toHour = if (toHour > from) toHour else from + 1)
    }

    /** Neues Fenster-Bis; das Von rutscht bei Bedarf eine Stunde davor. */
    fun withToHour(hour: Int): BibRoomAlarm {
        val to = hour.coerceIn(MIN_HOUR + 1, MAX_HOUR)
        return copy(toHour = to, fromHour = if (fromHour < to) fromHour else to - 1)
    }

    /** Einzeilige Status-Anzeige für die Raum-Alarm-Karte. */
    fun statusLine(hasFavorites: Boolean, today: LocalDate): String = when {
        !hasFavorites -> "Keine Lieblings-Räume markiert"
        else -> when (effectiveMode(today)) {
            BibAlarmMode.OFF -> "Aus"
            BibAlarmMode.TODAY_ONLY -> "Aktiv bis heute 23:59"
            BibAlarmMode.ALWAYS -> "Immer aktiv, ${clock(fromHour)} bis ${clock(toHour)}"
        }
    }

    companion object {
        const val DEFAULT_DURATION_MINUTES = 120
        const val DEFAULT_FROM_HOUR = 8
        const val DEFAULT_TO_HOUR = 20

        /** Bib-Buchungen laufen von 08:00 bis 20:00. */
        const val MIN_HOUR = 8
        const val MAX_HOUR = 20

        /** Wählbare Mindestdauern: 1 h oder 2 h (2 h ist zugleich das Buchungslimit). */
        val DURATION_OPTIONS: List<Int> = listOf(60, 120)

        val FROM_HOUR_OPTIONS: List<Int> = (MIN_HOUR until MAX_HOUR).toList()
        val TO_HOUR_OPTIONS: List<Int> = (MIN_HOUR + 1..MAX_HOUR).toList()

        /**
         * Standardmodus, solange noch keiner gespeichert ist. Wer schon vor dem
         * Raum-Alarm Lieblings-Räume markiert hatte, bekam bisher immer Meldungen;
         * das bleibt so ([BibAlarmMode.ALWAYS]). Alle anderen starten mit
         * [BibAlarmMode.OFF] und schalten den Alarm bewusst ein.
         */
        fun defaultModeFor(hasFavorites: Boolean): BibAlarmMode =
            if (hasFavorites) BibAlarmMode.ALWAYS else BibAlarmMode.OFF

        private fun clock(hour: Int): String = "%02d:00".format(hour)
    }
}
